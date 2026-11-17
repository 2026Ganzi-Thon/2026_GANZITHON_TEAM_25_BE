package com.mongle.backend.profile.dto;

import jakarta.validation.constraints.Pattern;

/**
 * 부분 업데이트 — 넘기지 않은 필드는 null로 두면 기존 값 유지(PATCH 시맨틱, /preferences와 동일).
 * accessory는 "없음"도 유효한 저장값이다(2026-11-18 성준님 결정) — null을 "없음"으로 쓰면
 * PATCH 규칙상 "변경 없음"으로 해석돼 사용자가 장식을 뺄 방법이 없어지기 때문이다.
 * 선택지는 형진님이 2026-11-15에 준 온보딩 설계 그대로.
 */
public record UpdateMongleProfileRequest(
        @Pattern(regexp = "멍하니 있기|잘 웃기|침 뱉기", message = "지원하지 않는 닮은 특징입니다")
        String similarityTrait,
        @Pattern(regexp = "차분하고 조용함|부드럽고 다정함|가끔 장난스러움", message = "지원하지 않는 편안한 성향입니다")
        String comfortStyle,
        @Pattern(regexp = "없음|리본\\(머리\\)|작은 꽃\\(머리\\)|모자|리본\\(목\\)|방울\\(목\\)", message = "지원하지 않는 장식입니다")
        String accessory) {
}
