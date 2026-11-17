package com.mongle.backend.profile;

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
 * Persistable 구현 이유는 AppPreference와 동일(그 클래스 주석 참고) — PK를 직접 넣는
 * 엔티티라 merge 경합에서 진 쪽이 승자의 row를 기본값으로 덮어쓰는 걸 막는다.
 *
 * onboardingCompleted는 컬럼이 아니라 계산값이다(2026-11-18 성준님 결정) — 별도 플래그를
 * 두면 필드 값과 플래그가 어긋날 수 있어서다. accessory만 기준으로 삼는 이유: 닮은 특징·
 * 편안한 성향은 "둘 다 건너뛰기 가능"(형진님 11/15)이라 영원히 null일 수 있지만, 장식은
 * 선택지에 "없음"이 포함돼 있어 온보딩을 거치면 항상 값이 채워진다.
 */
@Entity
@Table(name = "mongle_profile")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MongleProfile implements Persistable<String> {

    @Id
    @Column(name = "user_id", length = 36, nullable = false, updatable = false)
    private String userId;

    @Column(name = "similarity_trait", length = 50)
    private String similarityTrait;

    @Column(name = "comfort_style", length = 50)
    private String comfortStyle;

    @Column(name = "accessory", length = 30)
    private String accessory;

    @Transient
    private boolean isNew = false;

    public static MongleProfile defaultFor(String userId) {
        MongleProfile profile = new MongleProfile();
        profile.userId = userId;
        profile.isNew = true;
        return profile;
    }

    public void applyPatch(String similarityTrait, String comfortStyle, String accessory) {
        if (similarityTrait != null) this.similarityTrait = similarityTrait;
        if (comfortStyle != null) this.comfortStyle = comfortStyle;
        if (accessory != null) this.accessory = accessory;
    }

    public boolean isOnboardingCompleted() {
        return accessory != null;
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
