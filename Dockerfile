# Java 21 실행 환경 (실행만 하니까 JDK 대신 가벼운 JRE)
FROM eclipse-temurin:21-jre

# 컨테이너 안 작업 폴더
WORKDIR /app

# GitHub Actions에서 빌드한 jar를 이미지 안으로 복사
COPY build/libs/*.jar app.jar

# 이 컨테이너가 8080을 쓴다는 표시
EXPOSE 8080

# 컨테이너 시작 시 실행할 명령
ENTRYPOINT ["java", "-jar", "app.jar"]
