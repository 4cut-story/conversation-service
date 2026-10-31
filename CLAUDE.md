# conversation-service

대화 1건의 생성 흐름: 업로드 → STT → 장면 선택·콘티·대사 → 그림 → 4컷 합성. 상태·재시도·GPU 잠금. Spring Modulith 모듈 `pipeline`, `stt`, `scenario`, `comic`.

## 기준
- 상위 원칙: `docs`의 `.specify/memory/constitution.md`
- 아키텍처: `docs`의 `docs/architecture.md` (v3.0)
- **계약 기준 버전: common `v0.1.0` (태그 예정)** — 계약은 이 버전에 고정해 쓰고, 올릴 때 이 줄을 함께 고친다.
- 포트: 8080 (local 8082) (`common/service-catalog.yaml`)

## 기술
Java 21, Spring Boot 4, Spring AI(Ollama), Spring Modulith, Flyway, Spring Data JPA, AWS SDK v2(MinIO), Maven Wrapper, `common/parent-pom`.

## 규칙
- 서비스 간 통신은 `common/contracts`의 OpenAPI대로만 한다. API를 바꾸려면 `common` PR → 태그가 먼저다.
- 환경별 차이는 프로필과 환경 변수로만 둔다. 비밀 값은 커밋하지 않는다.
- 녹음과 전사 텍스트는 커밋하지 않는다.
- 테스트 이름은 한국어 `@DisplayName`(또는 테스트 설명)으로 쓰고 FR 번호와 연결한다.
