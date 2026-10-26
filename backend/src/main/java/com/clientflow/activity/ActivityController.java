package com.clientflow.activity;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.clientflow.activity.ActivityDtos.ActivityRequest;
import com.clientflow.activity.ActivityDtos.ActivityResponse;
import com.clientflow.common.PageResponse;

@RestController
@RequestMapping("/api/activities")
public class ActivityController {

    private final ActivityService service;

    public ActivityController(ActivityService service) {
        this.service = service;
    }

    // Timeline order: most recent first. / ترتيب السجل: الأحدث أولًا.
    @GetMapping
    public PageResponse<ActivityResponse> search(@RequestParam(required = false) Long companyId,
                                                 @RequestParam(required = false) Long dealId,
                                                 @RequestParam(required = false) Long contactId,
                                                 @RequestParam(required = false) ActivityType type,
                                                 @PageableDefault(size = 20, sort = {"occurredAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return service.search(companyId, dealId, contactId, type, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ActivityResponse create(@Valid @RequestBody ActivityRequest request) {
        return service.create(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
