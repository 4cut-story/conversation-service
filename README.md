# conversation-service

**녹음이나 기사 한 건을 4컷 만화로 완성할 때까지 단계를 차례로 돌리는 지휘자예요.**

[← 4cut story 소개](https://github.com/4cut-story) · [데모](https://4cut.java21.net) · [생성 흐름](https://github.com/4cut-story/.github/blob/main/docs/pipeline.md) · [AI 모델](https://github.com/4cut-story/.github/blob/main/docs/models.md)

## 하는 일

- 녹음 조각 받기 → 받아 적기 → 장면 고르기 → 콘티와 대사 → 그림 → 말풍선 넣어 합치기를 차례로 돌려요. 실패하면 그 단계부터 다시 해요.
- 녹음 대신 기사 주소를 받아 본문의 사실만 골라 새 대화로 각색해요 (`article`). 구독한 RSS 피드는 새 기사가 올라오면 저절로 만들어요 (`rss`).
- GPU가 필요한 일(받아 적기, 로컬 LLM, 그림, 대사 읽기)은 PostgreSQL 대기열(`SKIP LOCKED`)에 넣고, GPU PC 워커가 가져가게 해요.
- LLM은 Claude → Gemini → 로컬 Qwen3 8B 순서로 대체해요.
- 캐릭터 DB에서 콘티 인물에 어울리는 캐릭터를 골라 모든 컷에서 같은 얼굴로 그리게 해요 (`character`).
- 배경음과 가상 인물 목소리(`sound`, `voice`), 하루 생성 한도
- 완성한 만화는 content-service로 넘겨요.

## 시스템 안에서의 위치

```
web ──> api-gateway ──> conversation-service ──> PostgreSQL (상태, GPU 작업 대기열, pgvector)
                              │      ^           MinIO (녹음 조각, 그림)
                              │      └── GPU PC 워커가 HTTPS로 일을 가져감 (model-runtime)
                              ├──> Claude · Gemini API
                              └──> content-service (완성한 만화)
```

Spring Modulith로 모듈을 나눴어요: `pipeline`, `stt`, `scenario`, `comic`, `character`, `article`, `rss`, `sound`, `voice`, `conversations`.

## 기술

Java 21 · Spring Boot 4 · Spring Modulith · Spring AI · Spring Data JPA · Flyway · PostgreSQL + pgvector · AWS SDK v2 (MinIO)

## 실행

```bash
DB_URL=jdbc:postgresql://localhost:5432/fourcut DB_USERNAME=... DB_PASSWORD=... \
MINIO_ENDPOINT=http://localhost:9000 MINIO_ACCESS_KEY=... MINIO_SECRET_KEY=... MINIO_BUCKET=... \
SERVICES_CONTENT_URL=http://localhost:8083 \
ANTHROPIC_API_KEY=... GEMINI_API_KEY=... \
./mvnw spring-boot:run
```

로컬 포트는 8082예요. GPU 작업은 [model-runtime](https://github.com/4cut-story/model-runtime) 워커를 `WORKER_BACKEND=fake`로 띄우면 모델 없이 끝까지 돌려 볼 수 있어요.

## 검사

```bash
./mvnw verify          # 테스트, 정적 분석, 커버리지 80%
./mvnw spotless:apply  # 코드 스타일 고치기
```

## 자세한 문서

| 문서 | 내용 |
| --- | --- |
| [생성 흐름](https://github.com/4cut-story/.github/blob/main/docs/pipeline.md) | 녹음·기사에서 4컷까지 단계별 규칙, 캐릭터 찾기, 배경음, 가상 인물 목소리 |
| [AI 모델](https://github.com/4cut-story/.github/blob/main/docs/models.md) | 단계별 모델과 설정값 |
| [캐릭터 DB](docs/characters.md) | 캐릭터와 모습 테이블, 찾는 순서, 임베딩, 내부 API |
| [향후 개선](docs/backlog.md) | 남겨 둔 개선 과제 |
| [기능 스펙](https://github.com/4cut-story/docs/tree/main/specs) | 대화 4컷, 라이브 모드, RSS 구독, 제작 상태 |
