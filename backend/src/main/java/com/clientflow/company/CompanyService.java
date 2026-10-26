package com.clientflow.company;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clientflow.common.ApiException;
import com.clientflow.common.ErrorCode;
import com.clientflow.common.PageResponse;
import com.clientflow.common.Text;
import com.clientflow.common.Versions;
import com.clientflow.company.CompanyDtos.CompanyDetail;
import com.clientflow.company.CompanyDtos.CompanyRequest;
import com.clientflow.company.CompanyDtos.CompanyResponse;
import com.clientflow.contact.ContactRepository;
import com.clientflow.deal.DealRepository;
import com.clientflow.deal.DealStage;
import com.clientflow.user.CurrentUserService;

@Service
public class CompanyService {

    private static final EnumSet<DealStage> OPEN = EnumSet.of(DealStage.LEAD, DealStage.QUALIFIED,
            DealStage.PROPOSAL, DealStage.NEGOTIATION);

    private final CompanyRepository companies;
    private final ContactRepository contacts;
    private final DealRepository deals;
    private final CurrentUserService currentUser;

    public CompanyService(CompanyRepository companies, ContactRepository contacts, DealRepository deals,
                          CurrentUserService currentUser) {
        this.companies = companies;
        this.contacts = contacts;
        this.deals = deals;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public PageResponse<CompanyResponse> search(String q, Long ownerId, Pageable pageable) {
        String pattern = Text.likePattern(q);
        // Each filter is null when unused; Spring Data 4 rejects null parts, so they are dropped before allOf.
        // كل فلتر يكون null لو ما انستخدم؛ و Spring Data 4 يرفض الأجزاء الفاضية، فنشيلها قبل allOf.
        List<Specification<Company>> filters = Stream.<Specification<Company>>of(
                        pattern == null ? null : (root, query, cb) -> cb.or(
                                cb.like(cb.lower(root.get("name")), pattern, '\\'),
                                cb.like(cb.lower(root.get("city")), pattern, '\\'),
                                cb.like(cb.lower(root.get("industry")), pattern, '\\')),
                        ownerId == null ? null : (root, query, cb) -> cb.equal(root.get("owner").get("id"), ownerId))
                .filter(Objects::nonNull)
                .toList();
        return PageResponse.of(companies.findAll(Specification.allOf(filters), pageable), CompanyResponse::from);
    }

    @Transactional(readOnly = true)
    public CompanyDetail detail(Long id) {
        Company company = get(id);
        return new CompanyDetail(CompanyResponse.from(company),
                contacts.countByCompanyId(id),
                deals.countForCompany(id, OPEN),
                deals.sumAmountForCompany(id, OPEN),
                deals.sumAmountForCompany(id, EnumSet.of(DealStage.WON)));
    }

    @Transactional
    public CompanyResponse create(CompanyRequest r) {
        String name = r.name().trim();
        ensureNameIsFree(name, null);
        Company company = new Company(name, currentUser.resolveOwner(r.ownerId(), null));
        apply(company, r);
        return CompanyResponse.from(companies.save(company));
    }

    @Transactional
    public CompanyResponse update(Long id, CompanyRequest r) {
        Company company = get(id);
        currentUser.requireOwnerOrAdmin(company.getOwner());
        Versions.check(company.getVersion(), r.version());
        String name = r.name().trim();
        ensureNameIsFree(name, id);
        company.setName(name);
        company.setOwner(currentUser.resolveOwner(r.ownerId(), company.getOwner()));
        apply(company, r);
        // Flush now so the response carries the new version number (Hibernate bumps it on flush).
        // نحفظ الحين حتى يحمل الرد رقم النسخة الجديد (Hibernate يزيده عند الحفظ).
        companies.flush();
        return CompanyResponse.from(company);
    }

    @Transactional
    public void delete(Long id) {
        Company company = get(id);
        // Deals and contacts are real business history; deleting the company would erase them, so we refuse.
        // Its tasks and activity notes are removed with it (ON DELETE CASCADE).
        // الصفقات وجهات الاتصال تاريخ عمل حقيقي؛ حذف الشركة بيمسحها، فنرفض.
        // ومهامها وسجل تواصلها تنحذف معها (ON DELETE CASCADE).
        long dealCount = deals.countByCompanyId(id);
        long contactCount = contacts.countByCompanyId(id);
        if (dealCount > 0 || contactCount > 0) {
            throw new ApiException(ErrorCode.IN_USE, Map.of("deals", dealCount, "contacts", contactCount));
        }
        companies.delete(company);
    }

    public Company get(Long id) {
        return companies.findById(id).orElseThrow(() -> ApiException.notFound("company", id));
    }

    private void apply(Company c, CompanyRequest r) {
        c.setIndustry(Text.blankToNull(r.industry()));
        c.setCity(Text.blankToNull(r.city()));
        c.setPhone(Text.blankToNull(r.phone()));
        // Emails are stored lower-case so search and duplicates do not depend on capitalisation.
        // الإيميلات تنحفظ بأحرف صغيرة حتى البحث والتكرار ما يعتمدون على حالة الأحرف.
        String email = Text.blankToNull(r.email());
        c.setEmail(email == null ? null : email.toLowerCase());
        c.setWebsite(Text.blankToNull(r.website()));
        c.setNotes(Text.blankToNull(r.notes()));
    }

    private void ensureNameIsFree(String name, Long currentId) {
        companies.findByNameIgnoreCase(name)
                .filter(existing -> !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw new ApiException(ErrorCode.NAME_EXISTS);
                });
    }
}
