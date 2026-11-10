package net.java21.fourcut.conversation.article;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ExcludedArticleTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "[사설] 물가 대책 서둘러야",
        "[칼럼] 봄이 오는 소리",
        "[홍길동 칼럼] 골목의 기억",
        "【인터뷰】 30년 떡집 사장",
        "<기고> 지역 의료를 살리려면",
        "[기자수첩] 빈 상가",
        "[시론] 교통 정책",
        "(오피니언) 다시 생각하기",
        "Opinion: Why cities need trees",
        "Interview - The last baker",
        "[포토] 첫눈 내린 광화문",
        "[영상] 떡집 마지막 날",
        "[카드뉴스] 한눈에 보는 물가",
        "Photos: Snow in Seoul",
      })
  @DisplayName("004 FR-069: 제목 머리말로 칼럼·사설·인터뷰·사진·영상 기사를 가린다")
  void titles(String title) {
    assertThat(ExcludedArticle.matches(title, null, null)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://news.example.com/opinion/123",
        "https://news.example.com/news/column/2026/11/a.html",
        "https://news.example.com/editorial.html",
        "https://news.example.com/interview/9",
        "https://news.example.com/photo/2026/11/10/a",
        "https://news.example.com/video/77",
      })
  @DisplayName("004 FR-069: 주소 경로로 칼럼·사설·인터뷰·사진·영상 기사를 가린다")
  void paths(String url) {
    assertThat(ExcludedArticle.matches("제목", url, null)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"오피니언", "사설", "Opinion", "칼럼 > 데스크"})
  @DisplayName("004 FR-069: 기사 분류로 칼럼·사설·인터뷰를 가린다")
  void sections(String section) {
    assertThat(ExcludedArticle.matches("제목", "https://news.example.com/a/1", section)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "시장 떡집, 30년 만에 문 닫는다",
        "[속보] 지하철 2호선 운행 재개",
        "주민 인터뷰에서 나온 이야기로 본 재개발",
        "Columbus marathon draws record crowd",
      })
  @DisplayName("004 FR-069: 사실을 전하는 기사는 그대로 만든다")
  void news(String title) {
    assertThat(
            ExcludedArticle.matches(
                title, "https://news.example.com/society/2026/11/10/a.html", "사회"))
        .isFalse();
  }

  @org.junit.jupiter.api.Test
  @DisplayName("주소가 없거나 잘못돼도 오류 없이 false")
  void blanks() {
    assertThat(ExcludedArticle.matches(null, null, null)).isFalse();
    assertThat(ExcludedArticle.matches(null, "not a url %%", null)).isFalse();
    assertThat(ExcludedArticle.matches(null, " ", null)).isFalse();
  }
}
