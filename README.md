# conversation-service

대화 1건의 생성 흐름: 업로드 → STT → 장면 선택·콘티·대사 → 그림 → 4컷 합성. 상태·재시도·GPU 잠금. Spring Modulith 모듈 `pipeline`, `stt`, `scenario`, `comic`.

- 기술: Java 21, Spring Boot 4, Spring AI(Ollama), Spring Modulith, Flyway, Spring Data JPA, AWS SDK v2(MinIO), Maven Wrapper, `common/parent-pom`.
- 설계와 스펙: [docs](https://github.com/4cut-story/docs)
