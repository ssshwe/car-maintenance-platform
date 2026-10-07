package com.example.maintenance.auth;

import com.example.maintenance.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@Service
public class TokenService {
    private final byte[] secret;
    private final ObjectMapper json;
    private final long expireSeconds;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    public TokenService(@Value("${app.jwt.secret:}") String configured, @Value("${app.jwt.expire-minutes}") long minutes,
                        ObjectMapper json, Environment environment) {
        this.json = json; this.expireSeconds = minutes * 60;
        if (configured.isBlank()) {
            if (!environment.matchesProfiles("demo", "test")) throw new IllegalStateException("MySQL模式必须设置至少32字节的JWT_SECRET");
            secret = new byte[32]; new SecureRandom().nextBytes(secret);
        } else {
            secret = configured.getBytes(StandardCharsets.UTF_8);
            if (secret.length < 32) throw new IllegalStateException("JWT_SECRET需至少32字节");
        }
    }
    private byte[] sign(String input) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return mac.doFinal(input.getBytes(StandardCharsets.US_ASCII));
    }
    public String issue(Actor actor) {
        try {
            long now = Instant.now().getEpochSecond();
            String header = ENCODER.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
            String payload = ENCODER.encodeToString(json.writeValueAsBytes(Map.of("sub", actor.id().toString(), "role", actor.role(), "iss", "maintenance-platform", "iat", now, "exp", now + expireSeconds)));
            String value = header + "." + payload;
            return value + "." + ENCODER.encodeToString(sign(value));
        } catch (Exception e) { throw new IllegalStateException("无法生成令牌", e); }
    }
    public Long verify(String token) {
        try {
            if (token.length() > 4096) throw new IllegalArgumentException();
            String[] parts = token.split("\\."); if (parts.length != 3) throw new IllegalArgumentException();
            var header = json.readTree(Base64.getUrlDecoder().decode(parts[0]));
            if (!"HS256".equals(header.path("alg").asText())) throw new IllegalArgumentException();
            if (!MessageDigest.isEqual(sign(parts[0] + "." + parts[1]), Base64.getUrlDecoder().decode(parts[2]))) throw new IllegalArgumentException();
            var claims = json.readTree(Base64.getUrlDecoder().decode(parts[1]));
            if (!"maintenance-platform".equals(claims.path("iss").asText()) || claims.path("exp").asLong() <= Instant.now().getEpochSecond()) throw new IllegalArgumentException();
            return Long.valueOf(claims.path("sub").asText());
        } catch (Exception e) { throw new BusinessException("UNAUTHORIZED", "登录已过期，请重新登录", 401); }
    }
}
