# 1단계: Spring Boot 애플리케이션 빌드
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /app

# Gradle 관련 파일을 먼저 복사하여 의존성 캐시 활용
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Windows에서 생성된 줄바꿈 문제 방지 및 실행 권한 부여
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# 소스 코드 복사
COPY src src

# 실행 가능한 Spring Boot JAR 생성
RUN ./gradlew clean bootJar -x test --no-daemon

# 생성된 실행 JAR을 고정된 이름으로 복사
RUN JAR_FILE=$(find build/libs -name "*.jar" ! -name "*-plain.jar" | head -n 1) \
    && cp "$JAR_FILE" app.jar


# 2단계: 실제 실행 이미지
FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=builder /app/app.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]