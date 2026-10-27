package com.clientflow.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.clientflow.config.AppProperties;

/**
 * Limits the public POST endpoints per visitor IP: contact-form messages (spam), and sign-in, invite-link and
 * payment attempts (guessing passwords, tokens or card numbers).
 * يحدّ روابط POST العامة لكل عنوان IP: رسائل نموذج التواصل (الإزعاج)، ومحاولات الدخول ورابط الدعوة والدفع
 * (تخمين كلمات المرور أو الرموز أو أرقام البطاقات).
 *
 * <p>The IP comes from request.getRemoteAddr(). With server.forward-headers-strategy=native, Tomcat replaces it with
 * the X-Forwarded-For value only when the request came through a trusted internal proxy (our nginx).</p>
 * <p>العنوان من request.getRemoteAddr(). ومع server.forward-headers-strategy=native، Tomcat يبدله بقيمة
 * X-Forwarded-For بس لو الطلب جا من بروكسي داخلي موثوق (nginx حقنا).</p>
 */
@Component
public class PublicRateLimitFilter extends OncePerRequestFilter {

    private static final String INQUIRY_PATH = "/api/public/inquiries";
    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String INVITE_PREFIX = "/api/auth/invite/";
    private static final String PAYMENTS_PREFIX = "/api/public/payments/";

    private final RateLimiter inquiries;
    private final RateLimiter attempts;

    public PublicRateLimitFilter(AppProperties properties) {
        AppProperties.RateLimit config = properties.rateLimit();
        this.inquiries = new RateLimiter(config.inquiries(), config.window(), System::currentTimeMillis);
        this.attempts = new RateLimiter(config.attempts(), config.window(), System::currentTimeMillis);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !HttpMethod.POST.matches(request.getMethod()) || !(path.equals(INQUIRY_PATH)
                || path.equals(LOGIN_PATH) || path.startsWith(INVITE_PREFIX) || path.startsWith(PAYMENTS_PREFIX));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RateLimiter limiter = request.getRequestURI().equals(INQUIRY_PATH) ? inquiries : attempts;
        long retryAfter = limiter.hit(request.getRemoteAddr());
        if (retryAfter > 0) {
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(retryAfter));
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("{\"status\":429,\"title\":\"Too Many Requests\",\"code\":\"RATE_LIMITED\","
                    + "\"retryAfterSeconds\":" + retryAfter + "}");
            return;
        }
        chain.doFilter(request, response);
    }
}
