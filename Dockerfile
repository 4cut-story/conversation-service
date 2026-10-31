# conversation-service 실행 이미지. CI가 verify 단계에서 만든 jar(target/*.jar)를 넣는다.
FROM eclipse-temurin:21-jre
ENV TZ=Asia/Seoul
# root가 아닌 사용자로 실행한다.
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY target/*.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
