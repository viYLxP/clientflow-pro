package com.clientflow.inquiry;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.clientflow.common.PageResponse;
import com.clientflow.inquiry.InquiryDtos.ConvertRequest;
import com.clientflow.inquiry.InquiryDtos.ConvertResponse;
import com.clientflow.inquiry.InquiryDtos.InquiryResponse;

/**
 * The team's inbox of contact-form messages.
 * صندوق رسائل نموذج التواصل للفريق.
 */
@RestController
@RequestMapping("/api/inquiries")
public class InquiryController {

    private final InquiryService service;

    public InquiryController(InquiryService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<InquiryResponse> search(@RequestParam(required = false) InquiryStatus status,
                                                @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.search(status, pageable);
    }

    @GetMapping("/{id}")
    public InquiryResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/{id}/convert")
    public ConvertResponse convert(@PathVariable Long id, @Valid @RequestBody ConvertRequest request) {
        return service.convert(id, request);
    }

    @PostMapping("/{id}/dismiss")
    public InquiryResponse dismiss(@PathVariable Long id) {
        return service.dismiss(id);
    }
}
