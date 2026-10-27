package com.clientflow.inquiry;

import java.math.BigDecimal;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clientflow.activity.ActivityDtos.ActivityRequest;
import com.clientflow.activity.ActivityService;
import com.clientflow.activity.ActivityType;
import com.clientflow.common.ApiException;
import com.clientflow.common.PageResponse;
import com.clientflow.common.Text;
import com.clientflow.company.CompanyDtos.CompanyRequest;
import com.clientflow.company.CompanyRepository;
import com.clientflow.company.CompanyService;
import com.clientflow.contact.ContactDtos.ContactRequest;
import com.clientflow.contact.ContactRepository;
import com.clientflow.contact.ContactService;
import com.clientflow.deal.DealDtos.ContactRef;
import com.clientflow.deal.DealDtos.DealRequest;
import com.clientflow.deal.DealDtos.DealResponse;
import com.clientflow.deal.DealService;
import com.clientflow.inquiry.InquiryDtos.ConvertRequest;
import com.clientflow.inquiry.InquiryDtos.ConvertResponse;
import com.clientflow.inquiry.InquiryDtos.InquiryRequest;
import com.clientflow.inquiry.InquiryDtos.InquiryResponse;
import com.clientflow.user.AppUser;
import com.clientflow.user.CurrentUserService;

/**
 * Contact-form inquiries: received from visitors, then converted into a lead or dismissed by the team.
 * استفسارات نموذج التواصل: تجي من الزوار، وبعدها الفريق يحوّلها لصفقة أو يتجاهلها.
 */
@Service
public class InquiryService {

    private static final Logger log = LoggerFactory.getLogger(InquiryService.class);

    private final InquiryRepository inquiries;
    private final CompanyRepository companyRepository;
    private final CompanyService companies;
    private final ContactRepository contactRepository;
    private final ContactService contacts;
    private final DealService deals;
    private final ActivityService activities;
    private final CurrentUserService currentUser;

    public InquiryService(InquiryRepository inquiries, CompanyRepository companyRepository, CompanyService companies,
                          ContactRepository contactRepository, ContactService contacts, DealService deals,
                          ActivityService activities, CurrentUserService currentUser) {
        this.inquiries = inquiries;
        this.companyRepository = companyRepository;
        this.companies = companies;
        this.contactRepository = contactRepository;
        this.contacts = contacts;
        this.deals = deals;
        this.activities = activities;
        this.currentUser = currentUser;
    }

    /**
     * Saves a visitor's message. A filled honeypot is accepted silently and dropped, so the bot learns nothing.
     * يحفظ رسالة الزائر. ولو المصيدة معبّاة نقبل بصمت ونرميها، حتى الروبوت ما يعرف شي.
     */
    @Transactional
    public void receive(InquiryRequest r) {
        if (Text.blankToNull(r.website()) != null) {
            log.info("Contact form honeypot filled; message dropped");
            return;
        }
        String email = r.email().trim().toLowerCase();
        inquiries.save(new Inquiry(r.name().trim(), Text.blankToNull(r.companyName()), email,
                Text.blankToNull(r.phone()), r.message().trim()));
    }

    @Transactional(readOnly = true)
    public PageResponse<InquiryResponse> search(InquiryStatus status, Pageable pageable) {
        return PageResponse.of(status == null ? inquiries.findAll(pageable) : inquiries.findByStatus(status, pageable),
                InquiryResponse::from);
    }

    @Transactional(readOnly = true)
    public InquiryResponse get(Long id) {
        return InquiryResponse.from(inquiries.findById(id).orElseThrow(() -> ApiException.notFound("inquiry", id)));
    }

    /**
     * Creates (or reuses) the company and the contact, opens a LEAD deal, and logs the message on its timeline.
     * Everything goes through the normal services, so ownership and validation rules apply as usual.
     * ينشئ (أو يعيد استخدام) الشركة وجهة الاتصال، ويفتح صفقة LEAD، ويسجّل الرسالة في سجلها.
     * كله يمر من الخدمات العادية، فقواعد الملكية والتحقق تنطبق مثل العادة.
     */
    @Transactional
    public ConvertResponse convert(Long id, ConvertRequest r) {
        Inquiry inquiry = locked(id);
        AppUser me = currentUser.require();
        AppUser owner = currentUser.resolveOwner(r.ownerId(), null);
        // A visitor without a company is an individual client; their name becomes the account name.
        // الزائر بدون شركة عميل فرد؛ واسمه يصير اسم الحساب.
        String companyName = inquiry.getCompanyName() != null ? inquiry.getCompanyName() : inquiry.getName();
        long companyId = companyRepository.findByNameIgnoreCase(companyName)
                .map(c -> c.getId())
                .orElseGet(() -> companies.create(new CompanyRequest(companyName, null, null, null, null, null,
                        null, owner.getId(), null)).id());
        long contactId = contactRepository.findFirstByCompanyIdAndEmailIgnoreCase(companyId, inquiry.getEmail())
                .map(c -> c.getId())
                .orElseGet(() -> {
                    String[] names = splitName(inquiry.getName());
                    return contacts.create(new ContactRequest(names[0], names[1], inquiry.getEmail(),
                            inquiry.getPhone(), null, null, companyId, owner.getId(), null)).id();
                });
        String title = Text.blankToNull(r.dealTitle());
        DealResponse deal = deals.create(new DealRequest(title != null ? title : companyName, companyId, contactId,
                owner.getId(), r.amount() == null ? BigDecimal.ZERO : r.amount(), null, null, null));
        String note = "Website inquiry / استفسار من الموقع:\n" + inquiry.getMessage();
        activities.create(new ActivityRequest(ActivityType.NOTE, note.length() > 2000 ? note.substring(0, 2000) : note,
                inquiry.getCreatedAt(), companyId, deal.id(), contactId));
        inquiry.convert(deals.deal(deal.id()), me, Instant.now());
        return new ConvertResponse(InquiryResponse.from(inquiry), deal.id(), companyId,
                new ContactRef(contactId, contacts.contact(contactId).getFullName()));
    }

    @Transactional
    public InquiryResponse dismiss(Long id) {
        Inquiry inquiry = locked(id);
        inquiry.dismiss(currentUser.require(), Instant.now());
        return InquiryResponse.from(inquiry);
    }

    private Inquiry locked(Long id) {
        return inquiries.findForUpdate(id).orElseThrow(() -> ApiException.notFound("inquiry", id));
    }

    // "Fahad Al-Qahtani" → ("Fahad", "Al-Qahtani"); one word stays the first name. / الكلمة الوحدة تبقى الاسم الأول.
    static String[] splitName(String fullName) {
        String trimmed = fullName.trim();
        int space = trimmed.indexOf(' ');
        return space < 0 ? new String[]{trimmed, ""}
                : new String[]{trimmed.substring(0, space), trimmed.substring(space + 1).trim()};
    }
}
