/**
 * RSS 구독 (docs specs/004-rss-subscription). 사용자가 등록한 RSS 2.0·Atom 피드를 30분마다 확인해, 처음 보는 기사를 기록한 뒤
 * 기사로 만들기와 같은 흐름({@code ArticleReader} → {@code ConversationService#createFromFeed})으로 만화를 시작한다.
 * 피드는 기사와 같은 규칙(내부망 차단, 크기·시간 한도)으로 받는다. 바로 공개 피드의 만화는 완성될 때 content-service가 전체 공개로 게시한다. 이 모듈은 다른
 * 모듈에 공개 API를 내주지 않는다.
 */
package net.java21.fourcut.conversation.rss;
