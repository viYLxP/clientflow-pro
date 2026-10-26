package com.clientflow.deal;

import java.util.List;

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
import com.clientflow.deal.DealDtos.BoardColumn;
import com.clientflow.deal.DealDtos.DealRequest;
import com.clientflow.deal.DealDtos.DealResponse;
import com.clientflow.deal.DealDtos.StageChangeResponse;
import com.clientflow.deal.DealDtos.StageRequest;

@RestController
@RequestMapping("/api/deals")
public class DealController {

    private final DealService service;

    public DealController(DealService service) {
        this.service = service;
    }

    // Newest first by default; id breaks ties so paging never repeats or skips a row.
    // الأحدث أولًا افتراضيًا؛ والـ id يفصل بين المتساوين حتى التقسيم ما يكرر أو يتخطى صف.
    @GetMapping
    public PageResponse<DealResponse> search(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) DealStage stage,
                                             @RequestParam(required = false) DealService.Status status,
                                             @RequestParam(required = false) Long ownerId,
                                             @RequestParam(required = false) Long companyId,
                                             @RequestParam(required = false) Long contactId,
                                             @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return service.search(q, stage, status, ownerId, companyId, contactId, pageable);
    }

    @GetMapping("/board")
    public List<BoardColumn> board(@RequestParam(required = false) Long ownerId) {
        return service.board(ownerId);
    }

    @GetMapping("/{id}")
    public DealResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/{id}/history")
    public List<StageChangeResponse> history(@PathVariable Long id) {
        return service.history(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DealResponse create(@Valid @RequestBody DealRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public DealResponse update(@PathVariable Long id, @Valid @RequestBody DealRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/stage")
    public DealResponse changeStage(@PathVariable Long id, @Valid @RequestBody StageRequest request) {
        return service.changeStage(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
