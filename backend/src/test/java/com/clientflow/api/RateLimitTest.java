package com.clientflow.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import com.clientflow.support.IntegrationTest;

/**
 * The public endpoints refuse a visitor who sends too many messages, or too many sign-in, invite or payment attempts.
 * الروابط العامة ترفض الزائر اللي يرسل رسائل كثيرة، أو محاولات دخول أو دعوة أو دفع كثيرة.
 */
@TestPropertySource(properties = {"clientflow.rate-limit.inquiries=2", "clientflow.rate-limit.attempts=3"})
class RateLimitTest extends IntegrationTest {

    private static final String MESSAGE = """
            {"name":"Fahad","email":"fahad@example.com","message":"Please call me about a demo."}""";

    @Test
    void messagesAndAttemptsHaveSeparateLimitsPerIp() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/public/inquiries").contentType(MediaType.APPLICATION_JSON).content(MESSAGE))
                    .andExpect(status().isAccepted());
        }
        mvc.perform(post("/api/public/inquiries").contentType(MediaType.APPLICATION_JSON).content(MESSAGE))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        // Another IP is not affected. / عنوان ثاني ما يتأثر.
        mvc.perform(post("/api/public/inquiries").contentType(MediaType.APPLICATION_JSON).content(MESSAGE).with(r -> {
            r.setRemoteAddr("10.9.9.9");
            return r;
        })).andExpect(status().isAccepted());

        // Guessing invite tokens or passwords shares one limit. / تخمين رموز الدعوة أو كلمات المرور يشتركون بحد واحد.
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/auth/invite/check").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"token\":\"guess-" + i + "\"}")).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"guess\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"guess\"}"))
                .andExpect(status().isTooManyRequests());
    }
}
