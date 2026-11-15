# Mongle 프론트 연동 가이드

백엔드를 처음 붙이는 사람이 이 문서 하나로 시작할 수 있게 정리했다. 상세 스펙은 API 명세서 v1과 Swagger를 기준으로 하고, 여기엔 **붙일 때 실제로 헷갈리는 것**만 적었다. 아래 응답 예시는 로컬 서버에 실제로 호출해서 확인한 값이다.

## 1. 서버 띄우기

```bash
cp .env.example .env   # 채우는 방법은 README "로컬 세팅" 참고. JWT_SECRET은 필수, GEMINI_API_KEY는 비워도 서버는 뜸(AI는 폴백 문구)
docker compose up --build
```

| 항목 | 값 |
|---|---|
| Base URL | `http://localhost:8081` (**배포 URL은 아직 미정**) |
| Swagger | `http://localhost:8081/swagger-ui.html` — 우측 상단 Authorize에 토큰 넣고 바로 호출 가능 |
| CORS | 서버 기본값은 모든 오리진 허용(`ALLOWED_ORIGINS=*`). Vite 개발 서버(`http://localhost:5173`)에서 preflight 통과 확인. 허용 메서드 `GET,POST,PATCH,DELETE,OPTIONS`, 쿠키는 안 쓴다(Bearer 토큰) |

## 2. 인증 — 아이디·비밀번호 로그인 (2026-11-15 추가)

**로그인이 필수다.** 게스트(익명) 진입은 없고, 소셜 로그인도 없다. 기기를 바꿔도 같은 계정으로 로그인하면 기억·설정이 이어진다.

1. 첫 화면에서 로그인 또는 회원가입을 고른다.
2. 회원가입: `GET /auth/check-id`로 아이디 중복확인 → `POST /auth/signup`. **가입 응답에는 토큰이 없다.** 가입이 끝나면 첫 화면으로 돌아가 로그인한다.
3. 로그인: `POST /auth/login` → `accessToken`을 저장한다.
4. 이후 모든 요청에 `Authorization: Bearer {accessToken}`.
5. 로그아웃은 서버를 부르지 않고 저장한 토큰을 지우면 된다.

| 호출 | 요청 | 성공 응답 |
|---|---|---|
| `GET /auth/check-id?loginId=` | 쿼리 | 200 `{"available": true}` (쓸 수 있으면 `true`) |
| `POST /auth/signup` | `{name, loginId, password, termsAgreed, privacyAgreed}` | 201 `{"userId": "..."}` |
| `POST /auth/login` | `{loginId, password}` | 200 `{userId, accessToken, expiresAt, name}` |

```bash
curl -X POST http://localhost:8081/auth/signup -H "Content-Type: application/json" \
  -d '{"name":"김민섭","loginId":"demo2050a1","password":"mongle12!","termsAgreed":true,"privacyAgreed":true}'
# {"userId":"5d385d84-58df-4b82-bd30-cd61dc4793ae"}
curl -X POST http://localhost:8081/auth/login -H "Content-Type: application/json" \
  -d '{"loginId":"demo2050a1","password":"mongle12!"}'
# {"userId":"5d385d84-...","accessToken":"eyJ...","expiresAt":"2027-11-15T08:40:51Z","name":"김민섭"}
```

**입력 규칙** (서버 기준. 어긋나면 400 `VALIDATION_ERROR`와 문제 `field`)

| 필드 | 규칙 |
|---|---|
| `name` | 1~20자, 공백만 불가 (앞뒤 공백은 서버가 뗀다) |
| `loginId` | 6~20자, **영문 소문자+숫자만**, 영문과 숫자를 둘 다 포함. 대문자는 받지 않는다 |
| `password` | 6~64자, 영문+숫자+특수기호를 모두 포함, **영문·숫자·특수기호만**(한글·공백 불가) |
| `termsAgreed`, `privacyAgreed` | 둘 다 `true`여야 한다. 아니면 400 (`field`가 `termsAgreed`/`privacyAgreed`) |

- **필드 이름 주의:** 로그인 아이디는 `loginId`다. 응답의 `userId`는 서버 내부 ID(UUID)이고 로그인 아이디가 아니다.
- **아이디 중복:** 가입 때 이미 있는 아이디면 409 `CONFLICT`(`field: "loginId"`). 중복확인과 가입 사이에 다른 사람이 먼저 가입할 수 있으니 이 409는 화면에서 처리해야 한다.
- **로그인 실패:** 아이디가 없거나 비밀번호가 틀리면 둘 다 똑같이 401 `UNAUTHORIZED`("아이디 또는 비밀번호가 올바르지 않습니다")다. 어느 쪽이 틀렸는지는 알려주지 않는다.
- **호출 제한(429 `RATE_LIMITED`)**: 로그인은 접속 IP당 10분에 60회, **같은 아이디로 10번 틀리면 최대 10분간 그 아이디로 로그인할 수 없다(맞는 비밀번호도 막힌다)**. 가입은 IP당 10분 30회, 중복확인은 IP당 10분 120회. 같은 와이파이를 쓰는 기기가 많아도 보통은 문제없다.
- 토큰 유효기간은 **365일**이다. 토큰을 저장해두고 **401을 받으면 로그인 화면으로 보낸다**.
- **비밀번호 찾기는 없다.** 이메일을 받지 않아 복구할 방법이 없으니 잃어버리면 새로 가입해야 한다.
- `userId`는 서버가 토큰에서만 꺼낸다. 요청 바디나 파라미터에 `userId`를 실어 보내는 API는 없다.
- `POST /auth/anonymous`(기기 익명 인증)는 서버에 남아 있지만 **프론트는 쓰지 않는다.**

## 3. 엔드포인트 한눈에 보기 (`/auth/*`를 뺀 전부 토큰 필요)

| 용도 | 호출 | 성공 | 비고 |
|---|---|---|---|
| 세션 시작 | `POST /sessions` `{"entryType":"TALK"\|"SILENT"}` | 201 `{sessionId, entryType, startedAt}` | |
| 세션 종료 | `POST /sessions/{id}/finish` | 200 `{sessionId, status, endedAt}` | **몇 번 불러도 200**(뒤로가기+끝내기 동시에 눌러도 안전). 서버 쪽 대화 임시 컨텍스트가 이때 지워진다 |
| 공감 대화 | `POST /sessions/{id}/reflections` `{"text":"...","selectedMemoryIds":[]}` | 200 `{reply, riskDetected, riskResources}` | `text` 1~500자. **4절 참고** |
| 기억 목록 | `GET /memories` | 200 `{"memories":[{memoryId,summary,approvedAt,updatedAt}]}` | 최신순. **"이번만 이야기하기"에선 호출하지 말 것** |
| 기억 미리보기 | `POST /memories/preview` `{"sessionId":"..."}` | 201 `{previewId, draftSummary}` | 30분 안에 승인 안 하면 서버가 지운다 |
| 기억 승인 | `POST /memories` `{"previewId":"...","finalSummary":"..."}` | 201 `{memoryId, summary, approvedAt}` | 30개를 넘으면 응답에 `"warning":"memory_limit_near"`(저장은 됨, 안내만) |
| 기억 수정 | `PATCH /memories/{id}` `{"summary":"..."}` | 200 | |
| 기억 삭제 | `DELETE /memories/{id}` · `DELETE /memories` | 204 | 개별/전체. 하드 삭제 |
| 환경설정 | `GET /preferences` · `PATCH /preferences` | 200 `{soundEnabled,hapticsEnabled,animationEnabled}` | PATCH는 보낸 필드만 바뀐다. 처음 GET이면 3개 모두 `true`로 만들어 준다 |

기억은 **미리보기 → 사용자가 수정 → 승인** 순서다. 승인하지 않은 요약은 저장되지 않는다.

권장 호출 순서: **대화(reflections) → `POST /memories/preview` → (사용자 수정) → `POST /memories` → `POST /sessions/{id}/finish`**

- preview는 **반드시 finish 전에** 부른다. finish 때 서버의 대화 임시 컨텍스트가 지워져서, finish 후에 preview를 부르면 에러는 안 나지만 요약이 항상 `"오늘의 대화"`로만 나온다.
- approve는 finish 뒤에 해도 된다. preview 결과는 DB에 따로 저장돼 있어서 preview 후 30분 안이면 세션 종료와 상관없이 승인된다.

## 4. 공감 대화(reflections)에서 꼭 알아둘 것

```json
{ "reply": "약속이 또 취소되어서 마음이 많이 서운했을 것 같아요.", "riskDetected": false, "riskResources": null }
```

```json
{ "reply": "...", "riskDetected": true,
  "riskResources": [ {"name":"자살예방상담전화","phone":"109"},
                     {"name":"정신건강 위기상담전화","phone":"1577-0199"},
                     {"name":"긴급신고","phone":"119"} ] }
```

- **`riskDetected: true`면 `riskResources`를 화면에 보여준다.** 서버 응답 값을 그대로 쓰는 걸 권장한다(번호가 바뀌어도 서버만 고치면 됨). 대화는 끊지 않고 이어가며, 화면을 강제로 전환하지는 않는다.
- **응답 시간은 평균 5초 안팎**(2026-11-13 실측 15건: 평균 5.2초, 최대 7.6초)이다. 서버 쪽 Gemini 타임아웃이 8초이므로 **클라이언트 타임아웃은 10초 이상**으로 두고 로딩 상태를 보여줘야 한다.
- **Gemini가 실패해도 500이 아니라 200**이다. 이때 `reply`는 고정 문구("지금은 대답이 잘 안 떠올라…")이고, 응답만 봐서는 폴백인지 구분되지 않는다. 위험 신호는 폴백 상황에서도 키워드 기준으로 `riskDetected`가 유지된다.
- 한 세션에서 최대 **20회**까지 가능하고 초과하면 429 `RATE_LIMITED`다. 이미 끝난 세션이면 409 `CONFLICT`.
- "이번만 이야기하기"는 `selectedMemoryIds`를 **항상 빈 배열**로 보낸다.

## 5. 에러 포맷 (모든 엔드포인트 공통)

```json
{ "error": "VALIDATION_ERROR", "message": "비밀번호는 영문, 숫자, 특수기호를 모두 포함한 6~64자여야 하며 한글·공백은 쓸 수 없습니다", "field": "password" }
```

| HTTP | `error` | 언제 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | 형식/길이 오류, 깨진 JSON. `field`에 문제 필드(없을 수 있음) |
| 401 | `UNAUTHORIZED` | 토큰 없음·만료·위조 → **로그인 화면으로 보낸다**. `/auth/login`의 아이디·비밀번호 오류도 401 |
| 403 | `FORBIDDEN` | 남의 세션·기억 접근 |
| 404 | `NOT_FOUND` | 없는 리소스, 만료된 미리보기 |
| 409 | `CONFLICT` | 끝난 세션에 reflections 요청 / 가입 때 이미 있는 아이디(`field: loginId`) |
| 429 | `RATE_LIMITED` | 세션당 reflections 20회 초과 / 로그인·가입·중복확인 호출 제한 / 같은 아이디로 로그인 10회 실패 |
| 500 | `INTERNAL_ERROR` | 서버 오류(고정 문구) |

화면 분기에는 `error` 코드를 쓰고, `message`는 보여줘도 되는 한글 문장이다.

## 6. 서버를 부르지 않는 기능

양털 쓰다듬기·쓸어내리기·누르기, 털 깎기(상태 저장 안 함), 양털 반응 애니메이션, 대화 준비 템플릿 문장, 환경설정 on/off 즉시 반응(바뀔 때만 `PATCH /preferences`로 비동기 반영)은 **전부 클라이언트 처리**다.

## 7. 10초 스모크 테스트

```bash
BASE=http://localhost:8081
ID=smoke$(date +%s)   # 영문 소문자+숫자 6~20자
curl -s -X POST $BASE/auth/signup -H "Content-Type: application/json" \
  -d "{\"name\":\"테스트\",\"loginId\":\"$ID\",\"password\":\"smoke123!\",\"termsAgreed\":true,\"privacyAgreed\":true}"
TOKEN=$(curl -s -X POST $BASE/auth/login -H "Content-Type: application/json" \
  -d "{\"loginId\":\"$ID\",\"password\":\"smoke123!\"}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')
SID=$(curl -s -X POST $BASE/sessions -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"entryType":"TALK"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["sessionId"])')
curl -s -X POST $BASE/sessions/$SID/reflections -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"text":"오늘 친구가 약속을 또 취소해서 서운했어"}'
```

이게 `reply`를 돌려주면 서버·DB·인증이 모두 정상이다. `GEMINI_API_KEY`가 없으면 `reply`가 폴백 문구로 나온다(에러 아님).

## 8. 아직 정해지지 않은 것

- **배포 URL** — 정해지면 이 문서의 Base URL을 갱신한다.
- **로그인 응답의 온보딩 완료 여부** — 온보딩이 단순한 "봤는지" 플래그인지 저장할 데이터가 있는지 정해지면 추가한다(지금은 응답에 없다).
- 약관은 동의한 시각만 저장한다(약관 버전은 저장하지 않는다).
- 서버 데이터는 **로컬 개발용 DB**다. 배포 전까지는 컨테이너를 지우면(`docker compose down -v`) 데이터가 사라진다.
- 질문·버그는 백엔드 PR/이슈로 남겨주세요.
