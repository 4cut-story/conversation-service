# conversation-service

대화 1건의 생성 흐름: 업로드 → STT → 장면 선택·콘티·대사 → 그림 → 4컷 합성. 상태·재시도·GPU 잠금. Spring Modulith 모듈 `pipeline`, `stt`, `scenario`, `comic`.

- 기술: Java 21, Spring Boot 4, Spring AI(Ollama), Spring Modulith, Flyway, Spring Data JPA, AWS SDK v2(MinIO), Maven Wrapper, `common/parent-pom`.
- 설계와 스펙: [docs](https://github.com/4cut-story/docs)

## 캐릭터 DB (REQ-018)

`character` 모듈. 만화에 반복해 나오는 캐릭터를 `characters`(정체성: 이름, 직업, 성별, 나이대, IP-Adapter 세기)에, 컨셉(테마)·표정마다 다른 모습(외모 문구와 참조 그림)을 `character_looks`에 둔다. 같은 캐릭터가 일상에서는 의사 가운, 조선에서는 의녀 한복, 무협에서는 신의 도포를 입는다. 그림 단계(DRAW)에서 콘티 인물을 만화 컨셉의 기본 표정(NEUTRAL) 모습으로 찾아 GPU 워커에 IP-Adapter 참조로 넘긴다.

- **찾는 순서**: 콘티 가명과 이름이 정확히 같은 캐릭터의 그 컨셉 모습 → 없으면 콘티 외모 문구의 설명 임베딩(Gemini `gemini-embedding-001`, 768차원)과 코사인 거리가 가장 가까운 그 컨셉 기본 표정 모습(`characters.max-distance`, 기본 0.35). 한 만화에서 캐릭터 하나는 인물 하나에만 쓰고, 참조 그림이 올라온 모습만 쓴다.
- **표정**: NEUTRAL, HAPPY, SAD, ANGRY, SURPRISED, EMBARRASSED. 지금은 기본 표정만 찾고 참조로 쓴다. 표정 모습은 컷 감정에 맞춰 고르는 후속 작업용으로 둔다.
- **pgvector**: `character_looks.description_embedding vector(768)`과 HNSW 인덱스는 `afterMigrate.sql`이 pgvector가 있을 때만 만든다. 확장이 없고 만들 권한도 없으면 이름으로만 찾는다. DB 관리자가 `CREATE EXTENSION vector;`를 실행한 뒤 서비스를 다시 띄우면 컬럼이 생기고, `POST /internal/characters/reindex`로 기존 모습을 임베딩한다.
- **객체 키**: 참조 그림은 MinIO `characters/{캐릭터 id}/looks/{모습 id}/reference.png`, IP-Adapter embedding(약 650KB)은 같은 폴더의 `ip-adapter/{어댑터}.safetensors`다. embedding은 처음 그리는 작업에서 워커가 계산해 올리고, 그 뒤로는 받아서 쓴다.
- **그림 작업 입력**: 캐릭터가 나오는 컷의 `images[].references[]`에 `{character(모습 id), scale, region(full|left|right), embedGetKey}` 또는 embedding이 없을 때 `{imageGetKey, embedUploadPutKey}`를 넣는다. 두 명이 나오는 컷은 장면에 먼저 나온 인물이 왼쪽이다.

내부 API (게이트웨이 밖으로 열지 않는다):

```
POST   /internal/characters                      {name, look, concept?, description?, job?, gender?, ageGroup?, scale?, lookDescription?}
                                                 → {character, look, referenceUploadUrl} (첫 모습: 그 컨셉, NEUTRAL)
PUT    /internal/characters/{id}/looks/{concept}/{expression}   {look, description?} → {character, look, referenceUploadUrl}
PUT    {referenceUploadUrl}                      참조 그림 PNG (Content-Type: image/png)
GET    /internal/characters                      캐릭터 목록 (lookCount)
GET    /internal/characters/{id}                 캐릭터와 모든 모습
POST   /internal/characters/looks/{lookId}/reference   참조 그림 교체용 업로드 주소 (embedding은 다시 계산)
DELETE /internal/characters/{id}                 끄기 (지우지 않는다)
POST   /internal/characters/reindex              설명 임베딩이 없는 기본 표정 모습을 임베딩
```
