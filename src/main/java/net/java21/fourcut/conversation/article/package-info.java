/**
 * 기사로 만들기. 사용자가 넣은 기사 URL의 HTML을 서버가 안전하게 받아(내부망 주소 차단) 광고·메뉴를 걷어 낸 본문만 고르고, 외부 LLM으로 기사 속 사람들이 나누는
 * 대화로 각색해 녹음 흐름의 전사 자리에 넣는다. 이후 장면 고르기·콘티·그림·말풍선은 녹음과 같다. 공개 API는 {@link ArticleReader}, {@link
 * ArticleStore}, {@link ArticleDialogue}, {@link Article}, {@link ArticleException}, {@link
 * FeedDocument}이다. RSS 구독(rss 모듈)도 {@link ArticleReader#fetchFeed}로 같은 규칙으로 피드를 받는다.
 */
package net.java21.fourcut.conversation.article;
