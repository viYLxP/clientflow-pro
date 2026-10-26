package com.clientflow.contact;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.clientflow.common.PageResponse;
import com.clientflow.contact.ContactDtos.ContactRequest;
import com.clientflow.contact.ContactDtos.ContactResponse;

@RestController
@RequestMapping("/api/contacts")
public class ContactController {

    private final ContactService service;

    public ContactController(ContactService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ContactResponse> search(@RequestParam(required = false) String q,
                                                @RequestParam(required = false) Long companyId,
                                                @PageableDefault(size = 20, sort = {"firstName", "lastName", "id"}, direction = Sort.Direction.ASC) Pageable pageable) {
        return service.search(q, companyId, pageable);
    }

    @GetMapping("/{id}")
    public ContactResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContactResponse create(@Valid @RequestBody ContactRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public ContactResponse update(@PathVariable Long id, @Valid @RequestBody ContactRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
