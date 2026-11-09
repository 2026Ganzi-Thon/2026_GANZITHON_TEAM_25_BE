package com.mongle.backend.session;

/**
 * 세션이 끝났다는 신호. reflection 모듈(AI 담당)의 SessionContextStore가 이걸 듣고
 * 서버 메모리에 들고 있던 해당 세션의 대화 컨텍스트를 폐기한다(API_명세서_v1 4.1 규칙).
 * session 모듈이 reflection 모듈을 직접 참조하지 않도록 이벤트로 느슨하게 연결한다 —
 * 백엔드 A(session)와 백엔드 B(reflection)가 서로의 내부 구현을 몰라도 되게 하기 위함.
 */
public record SessionFinishedEvent(String sessionId) {
}
