# 캐릭터 DB

[← conversation-service README](../README.md)

대화 1건의 생성 흐름: 업로드 → STT → 장면 선택·콘티·대사 → 그림 → 4컷 합성. 상태·재시도·GPU 잠금. Spring Modulith 모듈 `pipeline`, `stt`, `scenario`, `comic`.

- 기술: Java 21, Spring Boot 4, Spring AI(Ollama), Spring Modulith, Flyway, Spring Data JPA, AWS SDK v2(MinIO), Maven Wrapper, `common/parent-pom`.
- 설계와 스펙: [docs](https://github.com/4cut-story/docs)


`character` 모듈. 만화에 반복해 나오는 캐릭터를 `characters`(정체성: 이름, 직업, 성별, 나이대, IP-Adapter 세기)에, 컨셉(테마)·표정마다 다른 모습(외모 문구와 참조 그림)을 `character_looks`에 둔다. 같은 캐릭터가 일상에서는 의사 가운, 조선에서는 의녀 한복, 무협에서는 신의 도포를 입는다. 그림 단계(DRAW)에서 콘티 인물을 만화 컨셉의 기본 표정(NEUTRAL) 모습으로 찾아 GPU 워커에 IP-Adapter 참조로 넘긴다.

- **찾는 순서**: 콘티 가명과 이름이 정확히 같은 캐릭터의 그 컨셉 모습 → 없으면 콘티 외모 문구의 설명 임베딩(OpenAI 호환 임베딩 서버의 BGE-M3, 1024차원, `characters.embedding-url`)과 코사인 거리가 가장 가까운 그 컨셉 기본 표정 모습(`characters.max-distance`, 기본 0.30). 임베딩은 머리·옷 색에 끌려 성별·나이가 다른 모습을 가깝게 내놓기 때문에, 가까운 후보 50개 가운데 외모 첫 마디(young woman, boy in hanbok, fox)의 동물·성별·나이가 어긋나지 않는 모습만 쓴다(`LookHead`). 한도 0.30은 질의 1,288개로 잰 값이다: 맞는 질의의 99% 이상을 받고, 캐릭터가 없는 질의(로봇, 용, 나무 등)는 대부분 0.30보다 멀다. 한 만화에서 캐릭터 하나는 인물 하나에만 쓰고, 참조 그림이 올라온 모습만 쓴다.
- **표정**: NEUTRAL, HAPPY, SAD, ANGRY, SURPRISED, EMBARRASSED. 지금은 기본 표정만 찾고 참조로 쓴다. 표정 모습은 컷 감정에 맞춰 고르는 후속 작업용으로 둔다.
- **pgvector**: `character_looks.description_embedding vector(1024)`과 HNSW 인덱스는 `afterMigrate.sql`이 pgvector가 있을 때만 만든다. 차원이 다른 옛 컬럼(Gemini 768차원)은 지우고 다시 만든다. 확장이 없고 만들 권한도 없으면 이름으로만 찾는다.
- **임베딩 채우기**: 서비스가 5초마다 임베딩이 없거나 다른 모델로 만든 기본 표정 모습을 32개씩 채운다(`characters.embed-interval`). 그래서 등록 스크립트는 임베딩 없이 넣어도 된다. 기다리지 않으려면 `POST /internal/characters/reindex`(최대 1000개).
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
PUT    /internal/gpu-jobs/character-looks/{lookId}  {before?, look} → {character, look, referenceUploadUrl}
                                                 GPU PC 워커용(게이트웨이가 워커 토큰 확인). 다시 그린 참조 그림과 함께 모습 문구를 바꾼다.
                                                 before가 지금 문구와 다르면 409, 이미 새 문구면 주소만 다시 준다
```
