package com.mongle.backend.preference;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppPreferenceRepository extends JpaRepository<AppPreference, String> {
}
