package net.java21.fourcut.conversation.article;

/**
 * 받은 피드 문서.
 *
 * @param url 넘겨주기를 따라간 마지막 주소
 * @param body 받은 바이트 (크기 한도까지)
 */
public record FeedDocument(String url, byte[] body) {}
