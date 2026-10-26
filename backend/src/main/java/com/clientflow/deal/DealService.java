package com.clientflow.deal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clientflow.common.ApiException;
import com.clientflow.common.CurrentUser;
import com.clientflow.common.ErrorCode;
import com.clientflow.common.PageResponse;
import com.clientflow.common.Text;
import com.clientflow.common.Versions;
import com.clientflow.company.Company;
import com.clientflow.company.CompanyService;
import com.clientflow.contact.Contact;
import com.clientflow.contact.ContactService;
import com.clientflow.deal.DealDtos.BoardColumn;
import com.clientflow.deal.DealDtos.DealRequest;
import com.clientflow.deal.DealDtos.DealResponse;
import com.clientflow.deal.DealDtos.StageChangeResponse;
import com.clientflow.deal.DealDtos.StageRequest;
import com.clientflow.deal.DealRepository.StageTotal;
import com.clientflow.user.CurrentUserService;

@Service
public class DealService {

    public static final EnumSet<DealStage> OPEN = EnumSet.of(DealStage.LEAD, DealStage.QUALIFIED,
            DealStage.PROPOSAL, DealStage.NEGOTIATION);

    // Cards loaded per board column. The column header still shows the full count and value.
    // عدد البطاقات المحمّلة لكل عمود. وعنوان العمود يعرض العدد والقيمة الكاملة.
    static final int BOARD_COLUMN_LIMIT = 50;

    /** Filter for open vs. closed deals. / فلتر الصفقات المفتوحة أو المغلقة. */
    public enum Status { OPEN, CLOSED }

    private final DealRepository deals;
    private final DealStageChangeRepository history;
    private final CompanyService companies;
    private final ContactService contacts;
    private final CurrentUserService currentUser;

    public DealService(DealRepository deals, DealStageChangeRepository history, CompanyService companies,
                       ContactService contacts, CurrentUserService currentUser) {
        this.deals = deals;
        this.history = history;
        this.companies = companies;
        this.contacts = contacts;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public PageResponse<DealResponse> search(String q, DealStage stage, Status status, Long ownerId, Long companyId,
                                             Long contactId, Pageable pageable) {
        String pattern = Text.likePattern(q);
        List<Specification<Deal>> filters = Stream.<Specification<Deal>>of(
                        pattern == null ? null : (root, query, cb) -> cb.or(
                                cb.like(cb.lower(root.get("title")), pattern, '\\'),
                                cb.like(cb.lower(root.get("company").get("name")), pattern, '\\')),
                        stage == null ? null : (root, query, cb) -> cb.equal(root.get("stage"), stage),
                        status == null ? null : (root, query, cb) -> root.get("stage")
                                .in(status == Status.OPEN ? OPEN : EnumSet.complementOf(OPEN)),
                        ownerId == null ? null : (root, query, cb) -> cb.equal(root.get("owner").get("id"), ownerId),
                        companyId == null ? null : (root, query, cb) -> cb.equal(root.get("company").get("id"), companyId),
                        contactId == null ? null : (root, query, cb) -> cb.equal(root.get("contact").get("id"), contactId))
                .filter(Objects::nonNull)
                .toList();
        return PageResponse.of(deals.findAll(Specification.allOf(filters), pageable), DealResponse::from);
    }

    @Transactional(readOnly = true)
    public DealResponse get(Long id) {
        return DealResponse.from(deal(id));
    }

    @Transactional
    public DealResponse create(DealRequest r) {
        DealStage stage = r.stage() == null ? DealStage.LEAD : r.stage();
        // A new deal starts open. Closing goes through changeStage, which asks for a lost reason and records it.
        // الصفقة الجديدة تبدأ مفتوحة. والإغلاق يمر عبر changeStage اللي يطلب سبب الخسارة ويسجّله.
        if (stage.isClosed()) {
            throw new ApiException(ErrorCode.INVALID_STAGE, Map.of("stage", stage.name()));
        }
        Company company = companies.get(r.companyId());
        Deal deal = new Deal(r.title().trim(), company, currentUser.resolveOwner(r.ownerId(), null));
        if (stage != DealStage.LEAD) {
            deal.moveTo(stage, null, Instant.now());
        }
        apply(deal, r, company);
        deals.save(deal);
        // First history row: from nothing to the starting stage. / أول صف في السجل: من لا شيء للمرحلة الأولى.
        history.save(new DealStageChange(deal, null, stage, CurrentUser.username(), Instant.now()));
        return DealResponse.from(deal);
    }

    @Transactional
    public DealResponse update(Long id, DealRequest r) {
        Deal deal = deal(id);
        currentUser.requireOwnerOrAdmin(deal.getOwner());
        Versions.check(deal.getVersion(), r.version());
        // A won or lost deal is a closed record (it counts in reports); reopen it before changing its terms.
        // الصفقة الرابحة أو الخاسرة سجل مغلق (ينحسب في التقارير)؛ لازم تنفتح من جديد قبل تعديل بياناتها.
        if (deal.getStage().isClosed()) {
            throw new ApiException(ErrorCode.INVALID_STAGE, Map.of("stage", deal.getStage().name()));
        }
        Company company = deal.getCompany().getId().equals(r.companyId()) ? deal.getCompany()
                : companies.get(r.companyId());
        deal.setTitle(r.title().trim());
        deal.setOwner(currentUser.resolveOwner(r.ownerId(), deal.getOwner()));
        apply(deal, r, company);
        deals.flush();
        return DealResponse.from(deal);
    }

    /**
     * Moves a deal along the pipeline (also used to win, lose or reopen it).
     * ينقل الصفقة بين مراحل خط المبيعات (ويُستخدم للفوز أو الخسارة أو إعادة الفتح).
     */
    @Transactional
    public DealResponse changeStage(Long id, StageRequest r) {
        Deal deal = deal(id);
        currentUser.requireOwnerOrAdmin(deal.getOwner());
        Versions.check(deal.getVersion(), r.version());
        String reason = Text.blankToNull(r.lostReason());
        // Same error shape as @Valid, so the form shows it under the reason field.
        // نفس شكل خطأ @Valid، حتى النموذج يعرضه تحت حقل السبب.
        if (r.stage() == DealStage.LOST && reason == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("lostReason", "NotBlank")));
        }
        Instant now = Instant.now();
        DealStage previous = deal.moveTo(r.stage(), reason, now);
        history.save(new DealStageChange(deal, previous, r.stage(), CurrentUser.username(), now));
        deals.flush();
        return DealResponse.from(deal);
    }

    /**
     * The pipeline board: one column per open stage. ownerId = null shows the whole team.
     * لوحة خط المبيعات: عمود لكل مرحلة مفتوحة. ownerId = null يعرض الفريق كامل.
     */
    @Transactional(readOnly = true)
    public List<BoardColumn> board(Long ownerId) {
        Map<DealStage, StageTotal> totals = deals.totalsByStage(OPEN, ownerId).stream()
                .collect(Collectors.toMap(StageTotal::getStage, Function.identity()));
        PageRequest limit = PageRequest.of(0, BOARD_COLUMN_LIMIT);
        return Arrays.stream(DealStage.values())
                .filter(OPEN::contains)
                .map(stage -> {
                    StageTotal total = totals.get(stage);
                    // A stage with no deals has no row in the GROUP BY result; show it as an empty column.
                    // المرحلة اللي ما فيها صفقات ما لها صف في نتيجة GROUP BY؛ نعرضها كعمود فاضي.
                    return new BoardColumn(stage, stage.probability(),
                            total == null ? 0 : total.getCount(),
                            total == null ? BigDecimal.ZERO : total.getAmount(),
                            deals.boardColumn(stage, ownerId, limit).stream().map(DealResponse::from).toList());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StageChangeResponse> history(Long id) {
        deal(id);
        return history.findByDealIdOrderByChangedAtAscIdAsc(id).stream().map(StageChangeResponse::from).toList();
    }

    @Transactional
    public void delete(Long id) {
        // Its stage history, tasks and activity notes go with it (ON DELETE CASCADE). Admin-only (SecurityConfig).
        // سجل المراحل والمهام وسجل التواصل تنحذف معها (ON DELETE CASCADE). للمدير فقط (SecurityConfig).
        deals.delete(deal(id));
    }

    public Deal deal(Long id) {
        return deals.findById(id).orElseThrow(() -> ApiException.notFound("deal", id));
    }

    private void apply(Deal deal, DealRequest r, Company company) {
        Contact contact = r.contactId() == null ? null : contacts.contact(r.contactId());
        // The contact must work at the deal's company, otherwise reports and timelines would mix up accounts.
        // جهة الاتصال لازم تكون من شركة الصفقة، وإلا التقارير والسجلات بتخلط بين الحسابات.
        if (contact != null && (contact.getCompany() == null || !contact.getCompany().getId().equals(company.getId()))) {
            throw new ApiException(ErrorCode.CONTACT_COMPANY_MISMATCH);
        }
        deal.setCompany(company);
        deal.setContact(contact);
        deal.setAmount(r.amount());
        deal.setExpectedCloseDate(r.expectedCloseDate());
    }
}
