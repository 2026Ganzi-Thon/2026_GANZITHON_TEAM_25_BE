# 멀티스테이지 빌드 — 배포 환경이 아직 안 정해져서(API_명세서_v1 9장) 로컬 docker-compose로
# 우선 띄울 수 있게만 해둔다. 실제 배포처가 정해지면 이 이미지를 그대로 쓰면 된다.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src/ src/
RUN ./mvnw -q -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
# root로 실행하면 앱이 뚫렸을 때 컨테이너 안에서 권한이 너무 크다 — 전용 비root 사용자로 돌린다.
RUN groupadd --system app && useradd --system --gid app --no-create-home --shell /usr/sbin/nologin app
COPY --from=build --chown=app:app /app/target/*.jar app.jar
USER app
EXPOSE 8081
# 앱이 뜬 뒤에만 정상으로 본다(Flyway 마이그레이션 포함). /actuator/health는 인증 없이 열려 있다.
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
  CMD curl -fs http://localhost:8081/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
