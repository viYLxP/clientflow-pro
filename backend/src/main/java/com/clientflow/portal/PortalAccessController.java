package com.clientflow.portal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.clientflow.portal.PortalDtos.InviteResponse;
import com.clientflow.portal.PortalDtos.PortalAccess;

/**
 * Staff endpoints to manage a contact's client portal login.
 * روابط الموظفين لإدارة دخول جهة الاتصال لبوابة العملاء.
 */
@RestController
@RequestMapping("/api/contacts/{contactId}/portal-access")
public class PortalAccessController {

    private final PortalAccessService service;

    public PortalAccessController(PortalAccessService service) {
        this.service = service;
    }

    @GetMapping
    public PortalAccess status(@PathVariable Long contactId) {
        return service.status(contactId);
    }

    @PostMapping("/invite")
    public InviteResponse invite(@PathVariable Long contactId) {
        return service.invite(contactId);
    }

    @PostMapping("/disable")
    public PortalAccess disable(@PathVariable Long contactId) {
        return service.disable(contactId);
    }
}
