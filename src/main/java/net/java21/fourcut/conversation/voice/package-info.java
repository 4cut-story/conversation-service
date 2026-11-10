/**
 * 가상 인물 목소리. 대사를 읽을 목소리(GPT-SoVITS v4 참조 음성) 목록을 서비스에 넣은 {@code voices/voices.json}으로 DB에 맞추고,
 * 목소리마다 한국어·영어 검색 글을 설명 임베딩(BGE-M3, pgvector)으로 바꿔 둔다. 콘티 인물 설명과 성별·나이대·사투리로 가장 가까운 목소리를 찾는다. 공개
 * API는 {@link VoiceLibrary#match}다.
 */
package net.java21.fourcut.conversation.voice;
