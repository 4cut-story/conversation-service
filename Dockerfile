# conversation-service 실행 이미지. CI가 verify 단계에서 만든 jar(target/*.jar)를 넣는다.
FROM eclipse-temurin:21-jre
ENV TZ=Asia/Seoul
# 말풍선 한글 합성(COMPOSE)용 글꼴. 없으면 한글이 네모로 깨진다 (FR-007)
RUN apt-get update \
    && apt-get install -y --no-install-recommends fonts-noto-cjk fontconfig \
    && rm -rf /var/lib/apt/lists/*
# root가 아닌 사용자로 실행한다.
RUN useradd --system --uid 10001 app
WORKDIR /app
# target에는 정적 분석 플러그인 jar도 생기므로 서비스 jar만 고른다
COPY target/conversation-service-*.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Djava.awt.headless=true", "-jar", "app.jar"]
