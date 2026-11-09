package com.mongle.backend.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, String> {
    Optional<AppUser> findByDeviceId(String deviceId);
}
