# Mongle 백엔드 배포 가이드 (서버 1대 + HTTPS)

시연은 프론트(Netlify, HTTPS)가 이 백엔드를 부르는 구조다. HTTPS 페이지는 HTTP 서버를 부를 수 없어서 **백엔드도 HTTPS 주소**가 필요하다. 이 문서는 AWS EC2 같은 서버 1대에 올리는 절차이고, 아래 파일을 쓴다.

| 파일 | 역할 |
|---|---|
| `deploy/docker-compose.prod.yml` | 운영용 구성 (MySQL + 백엔드 + Caddy). 개발용 `docker-compose.yml`과 다르다 |
| `deploy/Caddyfile` | 도메인으로 HTTPS를 받아 백엔드로 중계 (인증서는 Caddy가 자동 발급) |
| `deploy/prod.env.example` | 서버에 둘 환경변수 목록 (값은 비어 있다) |

```
브라우저(Netlify) ──HTTPS──▶ Caddy(80/443) ──▶ 백엔드(8081, 밖으로 안 열림) ──▶ MySQL(밖으로 안 열림)
```

## 1. 먼저 정할 것

| 항목 | 이유 | 상태 |
|---|---|---|
| AWS 계정·결제 담당 | 서버를 만드는 사람 | 미정 |
| **도메인** (예: `api.example.com`) | Caddy가 HTTPS 인증서를 받으려면 필요. A 레코드가 서버 공인 IP를 가리켜야 한다 | 미정 |
| 프론트 주소 (Netlify) | `ALLOWED_ORIGINS`에 넣는다. 여러 개면 쉼표로 구분 | 형진님 확인 |
| 해커톤의 서버 제공 여부 | 제공되면 AWS가 필요 없을 수 있다 | 공지 확인 필요 |

## 2. 서버 준비 (AWS)

- **인스턴스는 1대만.** 대화 컨텍스트와 인증 호출 제한이 서버 메모리에 있어서 여러 대로 늘리면 깨진다.
- **보안 그룹**: 인바운드 80, 443만 전체 공개. 22(SSH)는 내 IP만. **3306(MySQL)과 8081(백엔드)은 열지 않는다.** (이 구성은 어차피 호스트에 포트를 열지 않는다)
- 메모리는 Spring Boot와 MySQL이 같이 도니 여유를 둔다(이 레포에서 측정한 값은 없다, 2GB 이상을 권장하는 건 추정).
- Docker와 Docker Compose 설치는 [공식 문서](https://docs.docker.com/engine/install/)를 따른다.
- 도메인 DNS에서 A 레코드를 서버 공인 IP로 지정한다.

## 3. 비밀값 넣기

```bash
git clone https://github.com/2026Ganzi-Thon/2026_GANZITHON_TEAM_25_BE.git
cd 2026_GANZITHON_TEAM_25_BE
sudo mkdir -p /opt/mongle
sudo cp deploy/prod.env.example /opt/mongle/.env
sudo chown $USER /opt/mongle/.env && chmod 600 /opt/mongle/.env
nano /opt/mongle/.env      # 값을 채운다
```

| 변수 | 값 |
|---|---|
| `DB_PASSWORD`, `DB_ROOT_PASSWORD` | 이 서버에서만 쓰는 **새 랜덤 값** (개발용 `mongle-dev` 재사용 금지) |
| `JWT_SECRET` | `openssl rand -base64 48` 결과 |
| `GEMINI_API_KEY` | **김민섭이 서버에 직접 입력한다.** 채팅·레포·스크린샷에 붙이지 않는다. 비우면 AI는 폴백 문구로만 응답한다 |
| `ALLOWED_ORIGINS` | 프론트 주소. 지금 기본값 `*`는 운영에서 쓰지 말고 Netlify 주소로 좁힌다 |
| `SITE_ADDRESS` | 백엔드 도메인 (예: `api.example.com`) |

값에 `&` 같은 셸 특수문자가 있으면 따옴표로 감싼다. 필수 변수가 하나라도 비어 있으면 compose가 기동 전에 어떤 변수인지 알려주며 멈춘다.

## 4. 기동

```bash
docker compose -f deploy/docker-compose.prod.yml --env-file /opt/mongle/.env up -d --build
docker compose -f deploy/docker-compose.prod.yml --env-file /opt/mongle/.env ps      # 셋 다 Up, mysql·backend는 healthy
docker compose -f deploy/docker-compose.prod.yml --env-file /opt/mongle/.env logs -f backend
```

스키마는 첫 기동 때 Flyway가 만든다.

### 4.1 GitHub Actions 자동 배포

`main` push의 CI가 성공하면 CD가 그 커밋으로 EC2를 fast-forward하고 Compose를 다시 빌드한다. 서버에서 컨테이너 준비를 기다린 뒤 GitHub 러너에서 운영 HTTPS 헬스체크를 확인한다. CI를 통과한 커밋보다 원격 `main`이 앞서 있으면 그 배포는 건너뛴다.

저장소의 **Settings → Secrets and variables → Actions**에서 배포용 Secrets를 등록한다.

| Secret 이름 | 등록할 값 |
|---|---|
| `DEPLOY_SSH_HOST` | 운영 EC2의 SSH 주소. 호스트명 또는 IPv4만 입력 |
| `DEPLOY_SSH_KEY` | 입력 없이 인증할 수 있는 자동 배포 전용 SSH 개인키. 공개키는 서버의 `ubuntu` 계정에 등록 |
| `DEPLOY_SSH_KNOWN_HOSTS` | 서버에서 확인한 SSH 호스트 공개키의 `known_hosts` 한 줄. 호스트 필드는 `DEPLOY_SSH_HOST`와 동일하게 입력 |

- 러너에서 EC2의 SSH에 접근할 수 있어야 한다. 일반 GitHub-hosted 러너의 출발지 IP는 고정돼 있지 않다. 이미 허용된 전용 배포 러너를 사용한다면 Actions의 저장소 변수 `DEPLOY_RUNNER`에 그 러너의 라벨을 입력한다. [GitHub 러너의 IP 주소 안내](https://docs.github.com/en/actions/reference/runners/github-hosted-runners#ip-addresses)
- 첫 배포 전에 EC2의 `~/2026_GANZITHON_TEAM_25_BE`가 팀 저장소의 깨끗한 `main` 체크아웃이고 `ubuntu` 계정에서 Docker Compose를 실행할 수 있는지 확인한다. 런타임 설정은 서버의 `/opt/mongle/.env`를 사용한다.
- 초기 설정이 끝나면 **Actions → CD**에서 첫 배포 결과를 확인한다. 최신 커밋 배포가 실패하면 원인을 해결한 뒤 해당 CD 실행을 다시 돌린다.

## 5. 배포 후 점검

```bash
BASE=https://api.example.com
curl -s $BASE/actuator/health                                        # {"status":"UP"}
curl -si -X OPTIONS $BASE/sessions -H 'Origin: https://<프론트 주소>' \
  -H 'Access-Control-Request-Method: POST' | grep -i access-control-allow-origin   # 프론트 주소가 그대로 돌아와야 함
curl -so /dev/null -w '%{http_code}\n' $BASE/memories                # 401 (토큰 없음)
```

이어서 `docs/FRONTEND_GUIDE.md` 7절의 10초 스모크 테스트를 `BASE`만 바꿔서 실행한다. 이 스모크는 **Gemini를 1회 호출한다**(키가 있으면 비용 발생).

## 6. 운영

| 일 | 방법 |
|---|---|
| 업데이트 | `git pull` 후 4절의 `up -d --build`를 다시 실행 |
| 롤백 | 이전 커밋을 `git checkout`한 뒤 `up -d --build` (DB 스키마는 Flyway가 앞으로만 간다) |
| DB 백업 | `docker compose ... exec mysql sh -c 'mysqldump -u root -p"$MYSQL_ROOT_PASSWORD" mongle' > backup.sql` |
| 재시작 | `docker compose ... restart backend` — **대화 컨텍스트와 인증 호출 제한 기록이 초기화된다**(서버 메모리 상태) |
| 정지 | `docker compose ... down` (DB 데이터는 볼륨에 남는다. `-v`를 붙이면 지워진다) |

## 7. 비용·보안 주의

- **Gemini 키는 사비다.** 공개 URL이면 누구나 `/auth/anonymous`로 토큰을 받아 `/reflections`를 부를 수 있다. 앱 쪽 한도는 세션당 50회, 인증은 IP당 10분 30회다. 그 위에 **Google 쪽에서 일일 한도·예산 알림을 걸어두고**(메뉴 위치는 콘솔에서 직접 확인), 시연이 끝나면 키를 폐기하거나 재발급한다.
- 같은 와이파이를 쓰는 기기가 많으면 인증 한도(IP당 10분 30회)에 걸릴 수 있다. 프론트가 토큰을 저장하고 401일 때만 다시 받으면 문제없다(연동 설명서 참고).
- `/swagger-ui.html`은 운영에서도 열려 있다. 레포가 공개라 비밀은 아니지만, 닫고 싶으면 별도 결정이 필요하다.
- 비밀값이 든 `/opt/mongle/.env`는 서버 밖으로 꺼내지 않는다.

## 8. 2026-11-15 로컬에서 확인한 것 / 못 한 것

Gemini 키를 비운 채 이 구성을 그대로 로컬에서 띄워(도메인은 `localhost`, 임시 랜덤 비밀값) 확인했고 호출 비용은 0이다.

| 확인함 | 결과 |
|---|---|
| 필수 변수가 비면 기동 전에 중단 | 어떤 변수인지 메시지와 함께 실패 |
| MySQL(3306·3308)·백엔드(8081)가 호스트에 공개되지 않음 | 접속 불가 |
| HTTPS 경유로 health, 인증(200), 세션(201), 설정(200), 토큰 없음(401) | 정상 |
| CORS: 허용한 origin은 통과, 다른 origin은 403 | 정상 |
| `X-Forwarded-For`를 매번 위조해 36회 호출해도 인증 한도 우회 불가 | 30회째까지 통과, 이후 429 |

| 확인 못 함 | 이유 |
|---|---|
| 실제 AWS(EC2·보안 그룹·도메인) | 서버가 없다 |
| 공인 도메인의 Let's Encrypt 인증서 발급 | 로컬은 Caddy 내장 인증서를 썼다 |
| 도메인이 없을 때 IP 기반 주소(예: sslip.io류)로 인증서를 받는 방법 | 시도해보지 않았다. 되는지 확인이 필요하다 |
| Netlify 주소 형식, Netlify 프록시(rewrite) 사용 | 확인 안 함. 프록시를 쓰면 접속자 IP가 Netlify IP로 보여 인증 한도가 전원 합산될 수 있다 |
| Gemini 연동 | 키 없이 검증. 서버에서 키를 넣은 뒤 5절의 스모크로 확인 |
| 6절의 DB 백업·롤백·재시작 명령 | 실행해보지 않았다. 처음 쓸 때 한 번 확인할 것 |
