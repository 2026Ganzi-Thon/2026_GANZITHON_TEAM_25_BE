package com.mongle.backend.reflection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 2026-11-09 curl 검증 중 발견했던 "키워드에 공백이 있으면 조용히 매칭 안 되던 버그"의 회귀 방지용.
 * 그 사고는 유닛테스트 없이 실제 호출로만 잡혔던 것이라, 같은 종류의 문제를 코드 리뷰만으로
 * 다시 놓치지 않도록 생성자·matches() 동작을 직접 고정해둔다.
 */
class RiskKeywordDetectorTest {

    @Test
    void 키워드에_공백이_있어도_정규화돼서_매칭된다() {
        RiskKeywordDetector detector = new RiskKeywordDetector(List.of("극단적 선택"));

        assertThat(detector.matches("요즘 극단적선택을 생각해")).isTrue();
    }

    @Test
    void 빈_문자열이나_공백만_있는_키워드는_무시된다() {
        RiskKeywordDetector detector = new RiskKeywordDetector(List.of("자살", "", "   "));

        assertThat(detector.matches("   ")).isFalse();
        assertThat(detector.matches("자살하고 싶어")).isTrue();
    }

    @Test
    void 키워드가_문장_중간에_있어도_부분_포함으로_감지된다() {
        RiskKeywordDetector detector = new RiskKeywordDetector(List.of("죽고싶"));

        assertThat(detector.matches("나 요즘 너무 힘들어서 죽고싶다는 생각이 들어")).isTrue();
    }

    @Test
    void 매칭되는_키워드가_없으면_false() {
        RiskKeywordDetector detector = new RiskKeywordDetector(List.of("자살", "극단적 선택"));

        assertThat(detector.matches("오늘 날씨 좋고 기분이 괜찮아")).isFalse();
    }

    @Test
    void 빈_입력_텍스트는_매칭되지_않는다() {
        RiskKeywordDetector detector = new RiskKeywordDetector(List.of("자살"));

        assertThat(detector.matches("")).isFalse();
    }

    @Test
    void resources는_항상_안심콜_3종을_반환한다() {
        RiskKeywordDetector detector = new RiskKeywordDetector(List.of("자살"));

        assertThat(detector.resources()).hasSize(3);
        assertThat(detector.resources())
                .extracting("name", "phone")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("자살예방상담전화", "109"),
                        org.assertj.core.groups.Tuple.tuple("정신건강 위기상담전화", "1577-0199"),
                        org.assertj.core.groups.Tuple.tuple("긴급신고", "119"));
    }
}
