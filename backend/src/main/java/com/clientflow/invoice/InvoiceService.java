package com.clientflow.invoice;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
import com.clientflow.company.Company;
import com.clientflow.company.CompanyService;
import com.clientflow.config.AppProperties;
import com.clientflow.contact.Contact;
import com.clientflow.contact.ContactService;
import com.clientflow.deal.Deal;
import com.clientflow.deal.DealService;
import com.clientflow.invoice.InvoiceDtos.InvoiceDetail;
import com.clientflow.invoice.InvoiceDtos.InvoiceRequest;
import com.clientflow.invoice.InvoiceDtos.InvoiceResponse;
import com.clientflow.invoice.InvoiceDtos.InvoiceSummary;
import com.clientflow.invoice.InvoiceDtos.PortalInvoice;
import com.clientflow.payment.Payment;
import com.clientflow.payment.PaymentDtos.CheckoutResponse;
import com.clientflow.payment.PaymentService;
import com.clientflow.user.AppUser;
import com.clientflow.user.CurrentUserService;
import com.clientflow.user.UserRef;

/**
 * Invoice rules: drafts, issuing, voiding, bank transfers, and what the client portal may see and pay.
 * قواعد الفواتير: المسودات، والإصدار، والإلغاء، والتحويلات البنكية، واللي تقدر بوابة العميل تشوفه وتدفعه.
 *
 * <p>Everyone in the team can read invoices; only the company's owner or an admin can change them, the same
 * ownership rule as the rest of the CRM.</p>
 * <p>كل الفريق يقدر يقرأ الفواتير؛ وبس مالك الشركة أو المدير يقدر يغيّرها، نفس قاعدة الملكية في باقي النظام.</p>
 */
@Service
public class InvoiceService {

    private final InvoiceRepository invoices;
    private final InvoiceLock locks;
    private final CompanyService companies;
    private final ContactService contacts;
    private final DealService deals;
    private final PaymentService payments;
    private final CurrentUserService currentUser;
    private final AppProperties properties;

    public InvoiceService(InvoiceRepository invoices, InvoiceLock locks, CompanyService companies,
                          ContactService contacts, DealService deals, PaymentService payments,
                          CurrentUserService currentUser, AppProperties properties) {
        this.invoices = invoices;
        this.locks = locks;
        this.companies = companies;
        this.contacts = contacts;
        this.deals = deals;
        this.payments = payments;
        this.currentUser = currentUser;
        this.properties = properties;
    }

    /**
     * @param overdue true = only issued invoices past their due date / true = الفواتير الصادرة اللي فات استحقاقها بس
     */
    @Transactional(readOnly = true)
    public PageResponse<InvoiceSummary> search(String q, InvoiceStatus status, Long companyId, boolean overdue,
                                               Pageable pageable) {
        String pattern = Text.likePattern(q);
        LocalDate today = today();
        List<Specification<Invoice>> filters = Stream.<Specification<Invoice>>of(
                        pattern == null ? null : (root, query, cb) -> cb.or(
                                cb.like(cb.lower(root.get("number")), pattern, '\\'),
                                cb.like(cb.lower(root.get("company").get("name")), pattern, '\\')),
                        status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status),
                        companyId == null ? null : (root, query, cb) -> cb.equal(root.get("company").get("id"), companyId),
                        !overdue ? null : (root, query, cb) -> cb.and(
                                cb.equal(root.get("status"), InvoiceStatus.ISSUED),
                                cb.lessThan(root.get("dueDate"), today)))
                .filter(Objects::nonNull)
                .toList();
        return PageResponse.of(invoices.findAll(Specification.allOf(filters), pageable),
                i -> InvoiceSummary.from(i, today));
    }

    @Transactional(readOnly = true)
    public InvoiceDetail detail(Long id) {
        Invoice invoice = find(id);
        return new InvoiceDetail(InvoiceResponse.from(invoice, today()), UserRef.of(invoice.getCreatedBy()),
                canManage(invoice), payments.summary(invoice), payments.history(invoice));
    }

    @Transactional
    public InvoiceDetail create(InvoiceRequest r) {
        Company company = companies.get(r.companyId());
        AppUser me = currentUser.requireOwnerOrAdmin(company.getOwner());
        String number = "INV-%06d".formatted(invoices.nextNumber());
        Invoice invoice = new Invoice(number, company, me);
        apply(invoice, r);
        invoices.save(invoice);
        return detail(invoice.getId());
    }

    @Transactional
    public InvoiceDetail update(Long id, InvoiceRequest r) {
        Invoice invoice = find(id);
        requireManage(invoice);
        Versions.check(invoice.getVersion(), r.version());
        invoice.requireDraft();
        // Moving a draft to another company needs the right to invoice that company too.
        // نقل المسودة لشركة ثانية يحتاج صلاحية الفوترة لهالشركة كمان.
        if (!invoice.getCompany().getId().equals(r.companyId())) {
            Company company = companies.get(r.companyId());
            currentUser.requireOwnerOrAdmin(company.getOwner());
            invoice.setCompany(company);
        }
        apply(invoice, r);
        invoices.flush();
        return detail(id);
    }

    @Transactional
    public void delete(Long id) {
        Invoice invoice = find(id);
        requireManage(invoice);
        // Issued invoices are legal records: they are voided, never deleted. / الفواتير الصادرة سجلات رسمية: تنلغي، ما تنحذف.
        invoice.requireDraft();
        invoices.delete(invoice);
    }

    /**
     * Sends the invoice: it becomes visible and payable in the client portal, and can no longer be edited.
     * يصدر الفاتورة: تصير ظاهرة وتنْدفع في بوابة العميل، وما عاد تنعدّل.
     */
    @Transactional
    public InvoiceDetail issue(Long id, Long version) {
        Invoice invoice = find(id);
        requireManage(invoice);
        Versions.check(invoice.getVersion(), version);
        LocalDate today = today();
        if (invoice.getDueDate().isBefore(today)) {
            throw fieldError("dueDate", "FutureOrPresent");
        }
        // Gateways and bank transfers need at least 1 SAR. / البوابات والتحويلات تحتاج ريال على الأقل.
        if (invoice.getTotal().compareTo(BigDecimal.ONE) < 0) {
            throw fieldError("lines", "MinTotal");
        }
        invoice.issue(today, Instant.now());
        invoices.flush();
        return detail(id);
    }

    /**
     * Cancels an issued or paid invoice. A paid one is refunded first (through its gateway); if the refund fails,
     * nothing changes.
     * يلغي فاتورة صادرة أو مدفوعة. المدفوعة تنسترجع فلوسها أول (عن طريق بوابتها)؛ ولو فشل الاسترجاع ما يتغير شي.
     */
    @Transactional
    public InvoiceDetail voidInvoice(Long id, String reason, Long version) {
        Invoice invoice = find(id);
        requireManage(invoice);
        locks.lock(invoice);
        Versions.check(invoice.getVersion(), version);
        if (invoice.getStatus() == InvoiceStatus.ISSUED) {
            // A payment that just went through must be found now, so it is refunded and not lost.
            // الدفعة اللي توها نجحت لازم نلقاها الحين، حتى تنسترجع وما تضيع.
            payments.refreshPending(invoice);
        }
        if (invoice.getStatus() != InvoiceStatus.ISSUED && invoice.getStatus() != InvoiceStatus.PAID) {
            throw new ApiException(ErrorCode.INVALID_STATUS, Map.of("status", invoice.getStatus().name()));
        }
        payments.refundIfPaid(invoice);
        invoice.voidWith(reason.trim(), Instant.now());
        invoices.flush();
        return detail(id);
    }

    @Transactional
    public InvoiceDetail recordTransfer(Long id, String reference, Long version) {
        Invoice invoice = find(id);
        requireManage(invoice);
        locks.lock(invoice);
        Versions.check(invoice.getVersion(), version);
        payments.recordBankTransfer(invoice, Text.blankToNull(reference));
        invoices.flush();
        return detail(id);
    }

    // ----- Client portal / بوابة العميل -----

    @Transactional(readOnly = true)
    public PageResponse<InvoiceSummary> forClient(Long companyId, InvoiceStatus status, Pageable pageable) {
        LocalDate today = today();
        List<Specification<Invoice>> filters = Stream.<Specification<Invoice>>of(
                        (root, query, cb) -> cb.equal(root.get("company").get("id"), companyId),
                        (root, query, cb) -> cb.notEqual(root.get("status"), InvoiceStatus.DRAFT),
                        status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status))
                .filter(Objects::nonNull)
                .toList();
        return PageResponse.of(invoices.findAll(Specification.allOf(filters), pageable),
                i -> InvoiceSummary.from(i, today));
    }

    @Transactional(readOnly = true)
    public PortalInvoice forClient(Long companyId, String number) {
        Invoice invoice = clientInvoice(companyId, number);
        return new PortalInvoice(InvoiceResponse.from(invoice, today()), payments.summary(invoice));
    }

    @Transactional
    public CheckoutResponse payAsClient(Long companyId, String number, Payment.Method method) {
        Invoice invoice = clientInvoice(companyId, number);
        locks.lock(invoice);
        return payments.startOnline(invoice, method);
    }

    // Another company's invoice, or a draft, looks the same as a missing one. / فاتورة شركة ثانية، أو مسودة، تبان مثل غير الموجودة.
    private Invoice clientInvoice(Long companyId, String number) {
        return invoices.findByNumberAndCompanyId(number, companyId)
                .filter(i -> i.getStatus().visibleToClient())
                .orElseThrow(() -> ApiException.notFound("invoice", number));
    }

    // ----- Helpers / دوال مساعدة -----

    private void apply(Invoice invoice, InvoiceRequest r) {
        Company company = invoice.getCompany();
        Contact contact = r.contactId() == null ? null : contacts.contact(r.contactId());
        if (contact != null && (contact.getCompany() == null || !contact.getCompany().getId().equals(company.getId()))) {
            throw new ApiException(ErrorCode.CONTACT_COMPANY_MISMATCH);
        }
        Deal deal = r.dealId() == null ? null : deals.deal(r.dealId());
        if (deal != null && !deal.getCompany().getId().equals(company.getId())) {
            throw new ApiException(ErrorCode.DEAL_COMPANY_MISMATCH);
        }
        invoice.setContact(contact);
        invoice.setDeal(deal);
        invoice.setDueDate(r.dueDate());
        invoice.setNotes(Text.blankToNull(r.notes()));
        invoice.replaceLines(r.lines().stream()
                .map(l -> new Invoice.LineInput(l.description().trim(), l.quantity(), l.unitPrice()))
                .toList(), properties.vatRate());
    }

    private boolean canManage(Invoice invoice) {
        AppUser me = currentUser.require();
        return currentUser.isAdmin(me) || me.getId().equals(invoice.getCompany().getOwner().getId());
    }

    private void requireManage(Invoice invoice) {
        currentUser.requireOwnerOrAdmin(invoice.getCompany().getOwner());
    }

    private Invoice find(Long id) {
        return invoices.findDetail(id).orElseThrow(() -> ApiException.notFound("invoice", id));
    }

    private LocalDate today() {
        return LocalDate.now(properties.timeZone());
    }

    private static ApiException fieldError(String field, String rule) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of(field, rule)));
    }
}
