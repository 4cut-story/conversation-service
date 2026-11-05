# conversation-service

대화 1건의 생성 흐름: 업로드 → STT → 장면 선택·콘티·대사 → 그림 → 4컷 합성. 상태·재시도·GPU 잠금. Spring Modulith 모듈 `pipeline`, `stt`, `scenario`, `comic`.

- 기술: Java 21, Spring Boot 4, Spring AI(Ollama), Spring Modulith, Flyway, Spring Data JPA, AWS SDK v2(MinIO), Maven Wrapper, `common/parent-pom`.
- 설계와 스펙: [docs](https://github.com/4cut-story/docs)

## 캐릭터 DB (REQ-018)

`character` 모듈. 만화에 반복해 나오는 캐릭터의 외모 문구와 참조 그림을 `characters` 테이블에 두고, 그림 단계(DRAW)에서 콘티 인물을 찾아 GPU 워커에 IP-Adapter 참조로 넘긴다.

- **찾는 순서**: 콘티 가명과 이름이 정확히 같은 캐릭터 → 없으면 콘티 외모 문구의 설명 임베딩(Gemini `gemini-embedding-001`, 768차원)과 코사인 거리가 가장 가까운 캐릭터(`characters.max-distance`, 기본 0.35). 한 만화에서 캐릭터 하나는 인물 하나에만 쓰고, 참조 그림이 올라온 캐릭터만 쓴다.
- **pgvector**: `description_embedding vector(768)`과 HNSW 인덱스는 `afterMigrate.sql`이 pgvector가 있을 때만 만든다. 확장이 없고 만들 권한도 없으면 이름으로만 찾는다. DB 관리자가 `CREATE EXTENSION vector;`를 실행한 뒤 서비스를 다시 띄우면 컬럼이 생기고, `POST /internal/characters/reindex`로 기존 캐릭터를 임베딩한다.
- **IP-Adapter embedding**: 참조 그림의 embedding(약 650KB)은 DB가 아니라 MinIO `characters/{id}/ip-adapter/{어댑터}.safetensors`에 둔다. 처음 그리는 작업에서 워커가 계산해 올리고, 그 뒤로는 받아서 쓴다.
- **그림 작업 입력**: 캐릭터가 나오는 컷의 `images[].references[]`에 `{character, scale, region(full|left|right), embedGetKey}` 또는 embedding이 없을 때 `{imageGetKey, embedUploadPutKey}`를 넣는다. 두 명이 나오는 컷은 장면에 먼저 나온 인물이 왼쪽이다.

내부 API (게이트웨이 밖으로 열지 않는다):

```
POST   /internal/characters                  {name, look, description?, concept?, scale?} → {character, referenceUploadUrl}
PUT    {referenceUploadUrl}                  참조 그림 PNG (Content-Type: image/png)
GET    /internal/characters
POST   /internal/characters/{id}/reference   참조 그림 교체용 업로드 주소 (embedding은 다시 계산)
DELETE /internal/characters/{id}             끄기 (지우지 않는다)
POST   /internal/characters/reindex          설명 임베딩이 없는 캐릭터를 임베딩
```
