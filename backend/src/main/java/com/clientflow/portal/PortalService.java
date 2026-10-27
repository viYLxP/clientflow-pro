package com.clientflow.portal;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clientflow.common.ApiException;
import com.clientflow.common.ErrorCode;
import com.clientflow.common.PageResponse;
import com.clientflow.company.Company;
import com.clientflow.contact.Contact;
import com.clientflow.contact.ContactDtos.CompanyRef;
import com.clientflow.invoice.InvoiceDtos.InvoiceSummary;
import com.clientflow.invoice.InvoiceDtos.PortalInvoice;
import com.clientflow.invoice.InvoiceService;
import com.clientflow.invoice.InvoiceStatus;
import com.clientflow.payment.Payment;
import com.clientflow.payment.PaymentDtos.CheckoutResponse;
import com.clientflow.portal.PortalDtos.PortalMe;
import com.clientflow.user.AppUser;
import com.clientflow.user.CurrentUserService;
import com.clientflow.user.Role;

/**
 * What a signed-in client may do: see their details and their company's invoices, pay them, change password.
 * اللي يقدر يسويه العميل المسجّل: يشوف بياناته وفواتير شركته، ويدفعها، ويغيّر كلمة المرور.
 */
@Service
public class PortalService {

    private final CurrentUserService currentUser;
    private final InvoiceService invoices;
    private final PasswordEncoder passwordEncoder;

    public PortalService(CurrentUserService currentUser, InvoiceService invoices, PasswordEncoder passwordEncoder) {
        this.currentUser = currentUser;
        this.invoices = invoices;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public PortalMe me() {
        Contact c = client().getContact();
        Company company = c.getCompany();
        return new PortalMe(c.getFullName(), c.getEmail(), c.getPhone(), c.getJobTitle(),
                company == null ? null : new CompanyRef(company.getId(), company.getName()));
    }

    @Transactional(readOnly = true)
    public PageResponse<InvoiceSummary> invoices(InvoiceStatus status, Pageable pageable) {
        Long companyId = companyId();
        if (companyId == null) {
            return new PageResponse<>(List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0, 0);
        }
        return invoices.forClient(companyId, status, pageable);
    }

    @Transactional(readOnly = true)
    public PortalInvoice invoice(String number) {
        return invoices.forClient(requireCompanyId(number), number);
    }

    @Transactional
    public CheckoutResponse pay(String number, Payment.Method method) {
        return invoices.payAsClient(requireCompanyId(number), number, method);
    }

    @Transactional
    public void changePassword(String current, String next) {
        AppUser me = client();
        if (!passwordEncoder.matches(current, me.getPasswordHash())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("currentPassword", "Wrong")));
        }
        me.setPasswordHash(passwordEncoder.encode(next));
    }

    private AppUser client() {
        AppUser me = currentUser.require();
        // SecurityConfig already allows CLIENT only; this guards against a mistake there. / SecurityConfig يسمح لـ CLIENT بس؛ وهذا احتياط لو صار غلط هناك.
        if (me.getRole() != Role.CLIENT || me.getContact() == null) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return me;
    }

    // Staff may move the contact to another company; the portal then follows. / الموظف ممكن ينقل جهة الاتصال لشركة ثانية؛ والبوابة تتبعها.
    private Long companyId() {
        Company company = client().getContact().getCompany();
        return company == null ? null : company.getId();
    }

    private Long requireCompanyId(String number) {
        Long companyId = companyId();
        if (companyId == null) {
            throw ApiException.notFound("invoice", number);
        }
        return companyId;
    }
}
