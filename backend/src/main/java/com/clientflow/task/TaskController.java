package com.clientflow.task;

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
import com.clientflow.task.TaskDtos.TaskRequest;
import com.clientflow.task.TaskDtos.TaskResponse;
import com.clientflow.task.TaskDtos.VersionRequest;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    // Soonest due first. / الأقرب استحقاقًا أولًا.
    @GetMapping
    public PageResponse<TaskResponse> search(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) Long assigneeId,
                                             @RequestParam(required = false) TaskStatus status,
                                             @RequestParam(required = false) TaskService.Due due,
                                             @RequestParam(required = false) Long companyId,
                                             @RequestParam(required = false) Long dealId,
                                             @PageableDefault(size = 20, sort = {"dueDate", "id"}, direction = Sort.Direction.ASC) Pageable pageable) {
        return service.search(q, assigneeId, status, due, companyId, dealId, pageable);
    }

    @GetMapping("/{id}")
    public TaskResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@Valid @RequestBody TaskRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public TaskResponse update(@PathVariable Long id, @Valid @RequestBody TaskRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/complete")
    public TaskResponse complete(@PathVariable Long id, @Valid @RequestBody VersionRequest request) {
        return service.complete(id, request.version());
    }

    @PostMapping("/{id}/reopen")
    public TaskResponse reopen(@PathVariable Long id, @Valid @RequestBody VersionRequest request) {
        return service.reopen(id, request.version());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
