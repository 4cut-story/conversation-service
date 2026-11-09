/**
 * 배경음 라이브러리. 만화 장면에 붙일 장소·환경음, 효과음, 배경음악을 CC0·CC BY 클립으로 모아 두고, 한국어 설명 임베딩(BGE-M3)으로 글 검색을, 소리
 * 임베딩(CLAP)으로 비슷한 소리 찾기를 한다. 등록은 model-runtime의 {@code sound_library} 스크립트가 DB와 저장소에 직접 한다. 공개 API는
 * {@link SoundController}의 읽기 전용 경로뿐이다.
 */
package net.java21.fourcut.conversation.sound;
