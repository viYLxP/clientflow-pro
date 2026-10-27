package com.clientflow.invoice;

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
import com.clientflow.invoice.InvoiceDtos.InvoiceDetail;
import com.clientflow.invoice.InvoiceDtos.InvoiceRequest;
import com.clientflow.invoice.InvoiceDtos.InvoiceSummary;
import com.clientflow.invoice.InvoiceDtos.TransferRequest;
import com.clientflow.invoice.InvoiceDtos.VersionRequest;
import com.clientflow.invoice.InvoiceDtos.VoidRequest;

/**
 * Staff invoice endpoints. Status changes are separate POSTs, so each one is a clear, checked step.
 * روابط الفواتير للموظفين. تغييرات الحالة طلبات POST منفصلة، حتى كل وحدة خطوة واضحة ومتحقق منها.
 */
@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService service;

    public InvoiceController(InvoiceService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<InvoiceSummary> search(@RequestParam(required = false) String q,
                                               @RequestParam(required = false) InvoiceStatus status,
                                               @RequestParam(required = false) Long companyId,
                                               @RequestParam(defaultValue = "false") boolean overdue,
                                               @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.search(q, status, companyId, overdue, pageable);
    }

    @GetMapping("/{id}")
    public InvoiceDetail get(@PathVariable Long id) {
        return service.detail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceDetail create(@Valid @RequestBody InvoiceRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public InvoiceDetail update(@PathVariable Long id, @Valid @RequestBody InvoiceRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/{id}/issue")
    public InvoiceDetail issue(@PathVariable Long id, @Valid @RequestBody VersionRequest request) {
        return service.issue(id, request.version());
    }

    @PostMapping("/{id}/void")
    public InvoiceDetail voidInvoice(@PathVariable Long id, @Valid @RequestBody VoidRequest request) {
        return service.voidInvoice(id, request.reason(), request.version());
    }

    @PostMapping("/{id}/bank-transfer")
    public InvoiceDetail recordTransfer(@PathVariable Long id, @Valid @RequestBody TransferRequest request) {
        return service.recordTransfer(id, request.reference(), request.version());
    }
}
