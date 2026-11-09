package com.mongle.backend.memory;

/**
 * 세션의 (DB에는 없는, 서버 메모리상의) 대화 컨텍스트를 한 문장 요약으로 만드는 포트.
 * memory 모듈(백엔드 A)은 이 인터페이스만 알고, 실제 구현(Gemini 호출)은 reflection
 * 모듈(백엔드 B)의 GeminiMemorySummarizer가 제공한다 — 두 담당자가 서로의 내부를
 * 몰라도 각자 작업할 수 있게 하는 경계.
 */
public interface MemorySummarizer {
    String summarize(String sessionId);
}
