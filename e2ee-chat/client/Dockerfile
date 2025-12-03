# 1단계: 빌드
FROM gradle:8.6-jdk21 AS builder
WORKDIR /app
COPY . .

RUN gradle clean shadowJar --no-daemon

# 2단계: 실행
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=builder /app/build/libs/client-all.jar app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
