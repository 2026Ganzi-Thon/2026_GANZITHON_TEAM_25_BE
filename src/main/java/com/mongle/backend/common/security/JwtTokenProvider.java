package com.mongle.backend.common.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * API_명세서_v1 1.1 — 토큰 만료 365일, 기기 단위 익명 유저.
 * principal에는 userId(UUID 문자열) 하나만 담는다. 그 외 클레임은 두지 않는다
 * (요청 바디·파라미터의 userId를 믿지 않는 규칙과 짝 — userId의 유일한 출처는 이 토큰이다).
 */
@Component
public class JwtTokenProvider {

    private final SecretKey signingKey;
    private final Duration expiration;

    public JwtTokenProvider(
            @Value("${mongle.jwt.secret}") String secret,
            @Value("${mongle.jwt.expiration-days}") long expirationDays) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET 환경변수가 비어 있습니다. 32바이트(256비트) 이상의 랜덤 문자열이 필요합니다. "
                            + ".env는 docker compose만 자동으로 읽으니, ./mvnw spring-boot:run으로 띄울 땐 "
                            + "'set -a; source .env; set +a'로 셸에 올리거나 JWT_SECRET을 직접 export 하세요(README '로컬 세팅' 3번).");
        }
        try {
            this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
        } catch (WeakKeyException e) {
            throw new IllegalStateException(
                    "JWT_SECRET이 너무 짧습니다. HS256은 최소 32바이트가 필요합니다 (예: openssl rand -base64 48).", e);
        }
        this.expiration = Duration.ofDays(expirationDays);
    }

    public String generate(String userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(signingKey)
                .compact();
    }

    public Instant expirationFor(String token) {
        return Jwts.parser().verifyWith(signingKey).build()
                .parseSignedClaims(token).getPayload().getExpiration().toInstant();
    }

    /** 유효하면 userId, 아니면 empty — 필터에서 예외 대신 Optional로 흐름을 끊지 않고 401로 넘긴다. */
    public Optional<String> parseUserId(String token) {
        try {
            String subject = Jwts.parser().verifyWith(signingKey).build()
                    .parseSignedClaims(token).getPayload().getSubject();
            return Optional.ofNullable(subject);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
