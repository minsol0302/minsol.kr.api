# API Gateway 빌드
FROM eclipse-temurin:21-jdk AS api-builder
WORKDIR /app
# API Gateway 전체 디렉토리 복사
COPY api.minsol.kr/ .
RUN chmod +x gradlew && ./gradlew build -x test

# 최종 실행 이미지
FROM eclipse-temurin:21-jre
WORKDIR /app

# curl 설치
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# API Gateway JAR 파일 복사
COPY --from=api-builder /app/build/libs/*.jar ./api.jar

# API Gateway 실행 (OAuth Service와 Admin Service가 통합되어 있음)
ENTRYPOINT ["java", "-jar", "api.jar"]
