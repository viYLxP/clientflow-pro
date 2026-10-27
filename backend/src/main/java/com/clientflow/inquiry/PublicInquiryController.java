package com.clientflow.inquiry;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.clientflow.inquiry.InquiryDtos.InquiryRequest;

/**
 * The public contact form. No login; limited per IP by PublicRateLimitFilter.
 * نموذج التواصل العام. بدون دخول؛ ومحدود لكل IP عن طريق PublicRateLimitFilter.
 */
@RestController
@RequestMapping("/api/public/inquiries")
public class PublicInquiryController {

    private final InquiryService service;

    public PublicInquiryController(InquiryService service) {
        this.service = service;
    }

    // 202 Accepted with no body: the visitor only needs to know it arrived. / 202 بدون محتوى: الزائر يحتاج يعرف إنها وصلت بس.
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void send(@Valid @RequestBody InquiryRequest request) {
        service.receive(request);
    }
}
