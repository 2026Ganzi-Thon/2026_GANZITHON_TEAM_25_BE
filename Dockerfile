# 멀티스테이지 빌드 — 배포 환경이 아직 안 정해져서(API_명세서_v1 9장) 로컬 docker-compose로
# 우선 띄울 수 있게만 해둔다. 실제 배포처가 정해지면 이 이미지를 그대로 쓰면 된다.
FROM eclipse-temurin:24-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src/ src/
RUN ./mvnw -q -B package -DskipTests

FROM eclipse-temurin:24-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
