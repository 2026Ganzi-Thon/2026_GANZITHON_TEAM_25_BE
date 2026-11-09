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

    @Column(name = "device_id", length = 64, nullable = false, unique = true, updatable = false)
    private String deviceId;

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

    public void touch() {
        this.lastActiveAt = Instant.now();
    }
}
