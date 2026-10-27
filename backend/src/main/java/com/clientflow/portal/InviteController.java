package com.clientflow.portal;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.clientflow.auth.AuthController.LoginResponse;
import com.clientflow.portal.PortalDtos.AcceptInviteRequest;
import com.clientflow.portal.PortalDtos.InviteInfo;
import com.clientflow.portal.PortalDtos.TokenRequest;

/**
 * Public endpoints behind the invite link. POST (not GET) keeps the token out of URLs and logs; both are
 * rate-limited per IP.
 * روابط عامة خلف رابط الدعوة. POST (مو GET) يخلي الرمز برا الروابط والسجلات؛ والاثنين محدودين لكل IP.
 */
@RestController
@RequestMapping("/api/auth/invite")
public class InviteController {

    private final PortalAccessService service;

    public InviteController(PortalAccessService service) {
        this.service = service;
    }

    @PostMapping("/check")
    public InviteInfo check(@Valid @RequestBody TokenRequest request) {
        return service.check(request.token());
    }

    @PostMapping("/accept")
    public LoginResponse accept(@Valid @RequestBody AcceptInviteRequest request) {
        return service.accept(request.token(), request.password());
    }
}
