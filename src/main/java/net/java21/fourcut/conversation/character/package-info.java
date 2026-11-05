/**
 * 캐릭터 DB (REQ-018). 만화에 반복해 나오는 캐릭터의 외모 문구와 참조 그림을 두고, 콘티 인물을 이름이나 설명 임베딩(pgvector)으로 찾아 그림
 * 단계(comic)에 IP-Adapter 참조로 넘긴다. 공개 API는 {@link CharacterLibrary}와 {@link CharacterRef}다.
 */
package net.java21.fourcut.conversation.character;
