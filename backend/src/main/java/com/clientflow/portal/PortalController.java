package com.clientflow.portal;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
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
import com.clientflow.invoice.InvoiceDtos.InvoiceSummary;
import com.clientflow.invoice.InvoiceDtos.PortalInvoice;
import com.clientflow.invoice.InvoiceStatus;
import com.clientflow.payment.PaymentDtos.CheckoutResponse;
import com.clientflow.portal.PortalDtos.ChangePasswordRequest;
import com.clientflow.portal.PortalDtos.PortalMe;
import com.clientflow.portal.PortalDtos.PortalPayRequest;

/**
 * The client portal. SecurityConfig allows /api/portal/** to CLIENT accounts only, and every query here is limited
 * to the client's own company.
 * بوابة العملاء. SecurityConfig يسمح بـ /api/portal/** لحسابات CLIENT بس، وكل استعلام هنا محصور في شركة العميل نفسه.
 */
@RestController
@RequestMapping("/api/portal")
public class PortalController {

    private final PortalService service;

    public PortalController(PortalService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public PortalMe me() {
        return service.me();
    }

    @GetMapping("/invoices")
    public PageResponse<InvoiceSummary> invoices(@RequestParam(required = false) InvoiceStatus status,
                                                 @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.invoices(status, pageable);
    }

    @GetMapping("/invoices/{number}")
    public PortalInvoice invoice(@PathVariable String number) {
        return service.invoice(number);
    }

    @PostMapping("/invoices/{number}/pay")
    public CheckoutResponse pay(@PathVariable String number, @Valid @RequestBody PortalPayRequest request) {
        return service.pay(number, request.method());
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        service.changePassword(request.currentPassword(), request.newPassword());
    }
}
