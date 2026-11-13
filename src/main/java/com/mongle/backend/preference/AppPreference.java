package com.mongle.backend.preference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/**
 * Persistable을 구현하는 이유: PK(user_id)를 직접 넣는 엔티티라 Spring Data는 기본적으로
 * save()를 merge(SELECT 후 INSERT 또는 UPDATE)로 처리한다. 그러면 동시 생성 경합에서 진 쪽이
 * 이미 커밋된 승자의 row를 보고 기본값으로 UPDATE해버려, 승자가 막 반영한 PATCH를 덮어쓸 수 있다.
 * defaultFor()로 만든 객체는 isNew=true라 항상 persist(INSERT)로 가서, 이미 있으면 PK 위반으로 실패한다.
 */
@Entity
@Table(name = "app_preference")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppPreference implements Persistable<String> {

    @Id
    @Column(name = "user_id", length = 36, nullable = false, updatable = false)
    private String userId;

    @Column(name = "sound_enabled", nullable = false)
    private boolean soundEnabled;

    @Column(name = "haptics_enabled", nullable = false)
    private boolean hapticsEnabled;

    @Column(name = "animation_enabled", nullable = false)
    private boolean animationEnabled;

    @Transient
    private boolean isNew = false;

    public static AppPreference defaultFor(String userId) {
        AppPreference preference = new AppPreference();
        preference.userId = userId;
        preference.soundEnabled = true;
        preference.hapticsEnabled = true;
        preference.animationEnabled = true;
        preference.isNew = true;
        return preference;
    }

    public void applyPatch(Boolean soundEnabled, Boolean hapticsEnabled, Boolean animationEnabled) {
        if (soundEnabled != null) this.soundEnabled = soundEnabled;
        if (hapticsEnabled != null) this.hapticsEnabled = hapticsEnabled;
        if (animationEnabled != null) this.animationEnabled = animationEnabled;
    }

    @Override
    public String getId() {
        return userId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
