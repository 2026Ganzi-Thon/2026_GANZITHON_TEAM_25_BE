package com.mongle.backend.reflection;

import com.mongle.backend.reflection.dto.RiskResource;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * API_명세서_v1 4.3.1 — 1차 키워드 리스트(초안, 팀 검수 필요 🔴). 형태소 분석 없이 단순 포함
 * 매칭으로 시작한다. application.yml의 mongle.risk.keywords에서 팀이 바로 조정할 수 있다.
 */
@Component
public class RiskKeywordDetector {

    private static final List<RiskResource> RESOURCES = List.of(
            new RiskResource("자살예방상담전화(안심콜)", "0199"),
            new RiskResource("긴급신고", "119")
    );

    private final List<String> keywords;

    public RiskKeywordDetector(
            @Value("#{'${mongle.risk.keywords}'.split(',')}") List<String> keywords) {
        this.keywords = keywords.stream().map(String::trim).filter(k -> !k.isBlank()).toList();
    }

    public boolean matches(String text) {
        String normalized = text.replaceAll("\\s+", "");
        return keywords.stream().anyMatch(normalized::contains);
    }

    public List<RiskResource> resources() {
        return RESOURCES;
    }
}
