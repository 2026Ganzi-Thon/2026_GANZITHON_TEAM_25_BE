package com.mongle.backend.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "app_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA용 — 생성은 반드시 register()로
public class AppUser {

    @Id
    @Column(name = "user_id", length = 36, nullable = false, updatable = false)
    private String userId;

    /** 익명(기기) 유저만 값이 있다. 아이디·비밀번호로 가입한 유저는 null이다(V2 마이그레이션). */
    @Column(name = "device_id", length = 64, unique = true, updatable = false)
    private String deviceId;

    @Column(name = "login_id", length = 20, unique = true, updatable = false)
    private String loginId;

    /** BCrypt 해시만 저장한다. 평문 비밀번호는 어디에도 남기지 않는다. */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "name", length = 20)
    private String name;

    @Column(name = "terms_agreed_at")
    private Instant termsAgreedAt;

    @Column(name = "privacy_agreed_at")
    private Instant privacyAgreedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_active_at", nullable = false)
    private Instant lastActiveAt;

    public static AppUser register(String deviceId) {
        AppUser user = new AppUser();
        user.userId = java.util.UUID.randomUUID().toString();
        user.deviceId = deviceId;
        Instant now = Instant.now();
        user.createdAt = now;
        user.lastActiveAt = now;
        return user;
    }

    /** 아이디·비밀번호 가입. 약관 두 개에 동의한 시각을 같이 남긴다(동의 없이는 이 메서드까지 오지 않는다). */
    public static AppUser registerAccount(String name, String loginId, String passwordHash) {
        AppUser user = new AppUser();
        user.userId = java.util.UUID.randomUUID().toString();
        user.name = name;
        user.loginId = loginId;
        user.passwordHash = passwordHash;
        Instant now = Instant.now();
        user.termsAgreedAt = now;
        user.privacyAgreedAt = now;
        user.createdAt = now;
        user.lastActiveAt = now;
        return user;
    }

    public void touch() {
        this.lastActiveAt = Instant.now();
    }
}
