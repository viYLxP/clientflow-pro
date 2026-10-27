package com.clientflow.portal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * One-time invite tokens: 256 random bits for the link, and only their SHA-256 in the database.
 * رموز الدعوة لمرة وحدة: 256 بت عشوائية للرابط، وبس SHA-256 حقها في قاعدة البيانات.
 *
 * <p>SHA-256 (not BCrypt) is enough here: the token is long and random, so it cannot be guessed from its hash,
 * and a plain hash can be looked up with a unique index.</p>
 * <p>SHA-256 (مو BCrypt) يكفي هنا: الرمز طويل وعشوائي، فما ينخمّن من الـ hash، والـ hash العادي ينبحث عنه بفهرس فريد.</p>
 */
final class InviteTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private InviteTokens() {
    }

    // URL-safe text without padding, 43 characters. / نص آمن للروابط بدون حشو، 43 حرف.
    static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // Every Java runtime must ship SHA-256. / كل نسخة Java لازم فيها SHA-256.
            throw new IllegalStateException(e);
        }
    }
}
