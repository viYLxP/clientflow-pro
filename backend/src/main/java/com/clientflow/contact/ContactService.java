package com.clientflow.contact;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clientflow.common.ApiException;
import com.clientflow.common.PageResponse;
import com.clientflow.common.Text;
import com.clientflow.common.Versions;
import com.clientflow.company.CompanyService;
import com.clientflow.contact.ContactDtos.ContactRequest;
import com.clientflow.contact.ContactDtos.ContactResponse;
import com.clientflow.user.CurrentUserService;

@Service
public class ContactService {

    private final ContactRepository contacts;
    private final CompanyService companies;
    private final CurrentUserService currentUser;

    public ContactService(ContactRepository contacts, CompanyService companies, CurrentUserService currentUser) {
        this.contacts = contacts;
        this.companies = companies;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public PageResponse<ContactResponse> search(String q, Long companyId, Pageable pageable) {
        String pattern = Text.likePattern(q);
        // Search first name, last name, "first last" together, email and job title.
        // البحث في الاسم الأول والأخير و"الأول الأخير" مع بعض والإيميل والمسمى الوظيفي.
        List<Specification<Contact>> filters = Stream.<Specification<Contact>>of(
                        pattern == null ? null : (root, query, cb) -> cb.or(
                                cb.like(cb.lower(cb.concat(cb.concat(root.get("firstName"), " "), root.get("lastName"))), pattern, '\\'),
                                cb.like(cb.lower(root.get("email")), pattern, '\\'),
                                cb.like(cb.lower(root.get("jobTitle")), pattern, '\\')),
                        companyId == null ? null : (root, query, cb) -> cb.equal(root.get("company").get("id"), companyId))
                .filter(Objects::nonNull)
                .toList();
        return PageResponse.of(contacts.findAll(Specification.allOf(filters), pageable), ContactResponse::from);
    }

    @Transactional(readOnly = true)
    public ContactResponse get(Long id) {
        return ContactResponse.from(contact(id));
    }

    @Transactional
    public ContactResponse create(ContactRequest r) {
        Contact contact = new Contact(r.firstName().trim(), r.lastName().trim(), currentUser.resolveOwner(r.ownerId(), null));
        apply(contact, r);
        return ContactResponse.from(contacts.save(contact));
    }

    @Transactional
    public ContactResponse update(Long id, ContactRequest r) {
        Contact contact = contact(id);
        currentUser.requireOwnerOrAdmin(contact.getOwner());
        Versions.check(contact.getVersion(), r.version());
        contact.setFirstName(r.firstName().trim());
        contact.setLastName(r.lastName().trim());
        contact.setOwner(currentUser.resolveOwner(r.ownerId(), contact.getOwner()));
        apply(contact, r);
        contacts.flush();
        return ContactResponse.from(contact);
    }

    @Transactional
    public void delete(Long id) {
        // Deals, tasks and activities keep existing; their link to this person is cleared (ON DELETE SET NULL).
        // الصفقات والمهام وسجل التواصل تبقى؛ بس ينمسح ربطها بهذا الشخص (ON DELETE SET NULL).
        contacts.delete(contact(id));
    }

    public Contact contact(Long id) {
        return contacts.findById(id).orElseThrow(() -> ApiException.notFound("contact", id));
    }

    private void apply(Contact c, ContactRequest r) {
        String email = Text.blankToNull(r.email());
        c.setEmail(email == null ? null : email.toLowerCase());
        c.setPhone(Text.blankToNull(r.phone()));
        c.setJobTitle(Text.blankToNull(r.jobTitle()));
        c.setNotes(Text.blankToNull(r.notes()));
        c.setCompany(r.companyId() == null ? null : companies.get(r.companyId()));
    }
}
