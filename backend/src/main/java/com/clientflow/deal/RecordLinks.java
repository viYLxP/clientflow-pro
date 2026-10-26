package com.clientflow.deal;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.clientflow.common.ApiException;
import com.clientflow.common.ErrorCode;
import com.clientflow.company.Company;
import com.clientflow.company.CompanyService;
import com.clientflow.contact.Contact;
import com.clientflow.contact.ContactService;

/**
 * Resolves the company / deal / contact a task or activity is attached to, and keeps them consistent.
 * يحدد الشركة / الصفقة / جهة الاتصال اللي ترتبط فيها مهمة أو نشاط، ويخليها متوافقة.
 *
 * <p>Rules: a deal implies its company; a contact must work at that company; with only a contact,
 * the company is the contact's company. So a note on a deal always shows on its company's timeline too.</p>
 * <p>القواعد: الصفقة تحدد شركتها؛ وجهة الاتصال لازم تكون من نفس الشركة؛ ولو فيه جهة اتصال بس،
 * الشركة تكون شركة جهة الاتصال. فالملاحظة على صفقة تظهر دائمًا في سجل شركتها بعد.</p>
 */
@Service
public class RecordLinks {

    /** The resolved links; any of them may be null. / الروابط بعد التحديد؛ أي واحد منها ممكن يكون null. */
    public record Links(Company company, Deal deal, Contact contact) {
    }

    private final CompanyService companies;
    private final DealService deals;
    private final ContactService contacts;

    public RecordLinks(CompanyService companies, DealService deals, ContactService contacts) {
        this.companies = companies;
        this.deals = deals;
        this.contacts = contacts;
    }

    public Links resolve(Long companyId, Long dealId, Long contactId) {
        Deal deal = dealId == null ? null : deals.deal(dealId);
        Company company = companyId == null ? null : companies.get(companyId);
        if (deal != null) {
            // The client may send both; they must agree. / الواجهة ممكن ترسل الاثنين، ولازم يتطابقون.
            if (company != null && !company.getId().equals(deal.getCompany().getId())) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("companyId", "Mismatch")));
            }
            company = deal.getCompany();
        }
        Contact contact = contactId == null ? null : contacts.contact(contactId);
        if (contact != null) {
            if (company == null) {
                company = contact.getCompany();
            } else if (contact.getCompany() == null || !contact.getCompany().getId().equals(company.getId())) {
                throw new ApiException(ErrorCode.CONTACT_COMPANY_MISMATCH);
            }
        }
        return new Links(company, deal, contact);
    }
}
