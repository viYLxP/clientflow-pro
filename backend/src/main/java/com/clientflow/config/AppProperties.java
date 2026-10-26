package com.clientflow.config;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed application settings bound from the {@code clientflow.*} section of application.yml.
 * إعدادات التطبيق مربوطة بأنواع واضحة من قسم {@code clientflow.*} في application.yml.
 *
 * <p>{@code @Validated} makes the app refuse to start with an invalid configuration
 * (for example a JWT secret that is too short), instead of failing later at runtime.</p>
 * <p>{@code @Validated} يمنع التطبيق من التشغيل إذا كانت الإعدادات غير صالحة
 * (مثل مفتاح JWT قصير)، بدل ما يفشل لاحقًا أثناء الاستخدام.</p>
 */
@Validated
@ConfigurationProperties(prefix = "clientflow")
public record AppProperties(
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Cors cors,
        // Business time zone used for "today" and "this month" in reports and for overdue tasks (Asia/Riyadh by default).
        // المنطقة الزمنية للنشاط وتُستخدم لتحديد "اليوم" و"هذا الشهر" في التقارير والمهام المتأخرة (الرياض افتراضيًا).
        @NotNull ZoneId timeZone,
        @Valid @NotNull Seed seed) {

    /**
     * JWT signing settings.
     * إعدادات توقيع رموز JWT.
     *
     * @param secret     HMAC-SHA256 key; at least 32 chars (256 bits) as required by the algorithm
     *                   / مفتاح HMAC-SHA256 ولازم 32 حرفًا على الأقل (256 بت) حسب متطلبات الخوارزمية
     * @param expiration how long a token stays valid / مدة صلاحية الرمز
     */
    public record Jwt(@NotNull @Size(min = 32) String secret, @NotNull Duration expiration) {
    }

    /**
     * Allowed browser origins for CORS (the Angular dev server in development).
     * مصادر المتصفح المسموح لها عبر CORS (خادم Angular أثناء التطوير).
     */
    public record Cors(@NotNull List<String> allowedOrigins) {
    }

    /**
     * Demo data settings. Passwords come from environment variables, never hard-coded in production.
     * إعدادات البيانات التجريبية. كلمات المرور تأتي من متغيرات البيئة ولا تُكتب في الكود للإنتاج.
     */
    public record Seed(boolean enabled, String adminPassword, String salesPassword) {
    }
}
