package com.clientflow.company;

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
import com.clientflow.company.CompanyDtos.CompanyDetail;
import com.clientflow.company.CompanyDtos.CompanyRequest;
import com.clientflow.company.CompanyDtos.CompanyResponse;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyService service;

    public CompanyController(CompanyService service) {
        this.service = service;
    }

    // "id" breaks ties between equal names, so paging never repeats or skips a row.
    // "id" يفصل بين الأسماء المتساوية، حتى التنقل بين الصفحات ما يكرر أو يتخطى صف.
    @GetMapping
    public PageResponse<CompanyResponse> search(@RequestParam(required = false) String q,
                                                @RequestParam(required = false) Long ownerId,
                                                @PageableDefault(size = 20, sort = {"name", "id"}, direction = Sort.Direction.ASC) Pageable pageable) {
        return service.search(q, ownerId, pageable);
    }

    @GetMapping("/{id}")
    public CompanyDetail get(@PathVariable Long id) {
        return service.detail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompanyResponse create(@Valid @RequestBody CompanyRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public CompanyResponse update(@PathVariable Long id, @Valid @RequestBody CompanyRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
