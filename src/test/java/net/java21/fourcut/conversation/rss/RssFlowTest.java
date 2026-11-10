package net.java21.fourcut.conversation.rss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import net.java21.fourcut.conversation.TestcontainersConfiguration;
import net.java21.fourcut.conversation.comic.ContentClient;
import net.java21.fourcut.conversation.support.PublicId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * RSS 구독 흐름 (004): 피드를 등록하면 제목을 채우고, 첫 확인에서 가장 최근 기사 1편으로 대화를 시작한다. 새 기사가 올라온 뒤 "지금 확인"을 누르면 그 기사만
 * 만든다. 대화에는 구독과 바로 공개 여부가 남는다. 비슷한 기사 거르기는 단위 테스트(SimilarArticlesTest, FeedCheckerTest)가 본다.
 */
@SpringBootTest(
    properties = {
      "pipeline.recovery-interval=1h",
      "pipeline.local-recovery-interval=1h",
      "conversation.audio-cleanup-delay=1h",
      "article.allow-private=true",
      "rss.initial-delay=1h",
      "rss.similar-within=0s",
      "DB_SCHEMA=public"
    })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RssFlowTest {

  private static final String PARAGRAPH =
      "동네 시장 한가운데에 30년 된 떡집이 문을 닫는다는 소식이 전해지자 단골손님들이 아침부터 줄을 섰다. 주인 할머니는 그동안 고마웠다며 남은 떡을 모두"
          + " 나눠 주었고, 손님들은 꽃다발과 손편지로 답했다. 가게 자리에는 손주가 카페를 열 예정이라 단골들은 다시 오겠다고 약속했다. 이웃 상인들은 떡집 앞에"
          + " 작은 현수막을 걸어 30년 동안 시장을 지켜 준 할머니에게 고마운 마음을 전했고, 할머니는 눈시울을 붉혔다.";

  private static final List<String> ARTICLES = new CopyOnWriteArrayList<>();
  private static HttpServer server;
  private static String base;

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  @MockitoBean private ContentClient content;
  @MockitoBean private net.java21.fourcut.conversation.character.TextEmbedder embedder;

  @BeforeAll
  static void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/rss",
        ex -> {
          StringBuilder xml =
              new StringBuilder(
                  "<?xml version=\"1.0\"?><rss version=\"2.0\"><channel><title>예시신문</title>"
                      + "<link>https://news.example.com/</link>");
          for (String n : ARTICLES.reversed()) {
            xml.append("<item><title>기사 ")
                .append(n)
                .append("</title><link>")
                .append(base)
                .append("/news/")
                .append(n)
                .append("</link><guid>news-")
                .append(n)
                .append("</guid></item>");
          }
          xml.append("</channel></rss>");
          send(ex, "application/rss+xml; charset=utf-8", xml.toString());
        });
    server.createContext(
        "/news",
        ex ->
            send(
                ex,
                "text/html; charset=utf-8",
                "<html lang=\"ko\"><head><meta property=\"og:title\" content=\"떡집 "
                    + ex.getRequestURI().getPath()
                    + "\"><meta property=\"og:site_name\" content=\"예시신문\"></head><body><article><p>"
                    + PARAGRAPH
                    + "</p><p>"
                    + PARAGRAPH
                    + "</p></article></body></html>"));
    server.createContext(
        "/page", ex -> send(ex, "text/html", "<html><body><p>그냥 웹 페이지</p></body></html>"));
    server.start();
    base = "http://127.0.0.1:" + server.getAddress().getPort();
  }

  private static void send(com.sun.net.httpserver.HttpExchange ex, String type, String text)
      throws IOException {
    byte[] body = text.getBytes(StandardCharsets.UTF_8);
    ex.getResponseHeaders().add("Content-Type", type);
    ex.sendResponseHeaders(200, body.length);
    ex.getResponseBody().write(body);
    ex.close();
  }

  @AfterAll
  static void stopServer() {
    server.stop(0);
  }

  @AfterEach
  void cleanUp() {
    jdbc.update("delete from rss_feeds");
    jdbc.update("delete from conversations");
    ARTICLES.clear();
  }

  @Test
  @DisplayName("004 US1·US2·US3: 등록하면 최근 기사 1편으로 시작하고, 새 기사는 지금 확인으로 만들며, 대화에 바로 공개가 남는다")
  void subscribeAndMake() throws Exception {
    String owner = PublicId.generate();
    ARTICLES.addAll(List.of("1", "2", "3"));

    String created =
        body(
            mvc.perform(
                    as(post("/api/v1/rss-feeds"), owner)
                        .content(
                            "{\"url\":\""
                                + base
                                + "/rss\",\"autoPublish\":true,\"concept\":\"NEWSPAPER\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.title").value("예시신문"))
                .andExpect(jsonPath("$.autoPublish").value(true))
                .andExpect(jsonPath("$.concept").value("NEWSPAPER")));
    String id = JsonPath.read(created, "$.id");

    await(() -> conversations(owner) == 1);
    Map<String, Object> first =
        jdbc.queryForMap(
            "select source, source_url, auto_publish, rss_feed_id, concept from conversations"
                + " where owner_id = ?",
            owner);
    assertThat(first.get("source")).isEqualTo("ARTICLE");
    assertThat(first.get("source_url")).isEqualTo(base + "/news/3");
    assertThat(first.get("auto_publish")).isEqualTo(true);
    assertThat(first.get("rss_feed_id")).isNotNull();
    assertThat(first.get("concept")).isEqualTo("NEWSPAPER");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from rss_feed_items where status = 'SKIPPED'", Integer.class))
        .isEqualTo(2);

    ARTICLES.addAll(List.of("4", "5"));
    mvc.perform(as(post("/api/v1/rss-feeds/{id}/check", id), owner))
        .andExpect(status().isAccepted());
    await(() -> conversations(owner) == 3);
    await(() -> made(id) == 3);

    mvc.perform(as(get("/api/v1/rss-feeds"), owner))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].made").value(3))
        .andExpect(jsonPath("$.items[0].lastCheckedAt").isNotEmpty())
        .andExpect(jsonPath("$.items[0].recent.length()").value(5));

    mvc.perform(
            as(put("/api/v1/rss-feeds/{id}", id), owner)
                .content("{\"autoPublish\":false,\"enabled\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(false))
        .andExpect(jsonPath("$.concept").value("AUTO"));

    mvc.perform(as(delete("/api/v1/rss-feeds/{id}", id), owner)).andExpect(status().isNoContent());
    assertThat(jdbc.queryForObject("select count(*) from rss_feed_items", Integer.class)).isZero();
    assertThat(conversations(owner)).isEqualTo(3);
  }

  @Test
  @DisplayName("004 FR-051·FR-053·FR-054: 피드가 아닌 주소는 422, 같은 주소는 409, 남의 구독은 404")
  void rejects() throws Exception {
    String owner = PublicId.generate();
    mvc.perform(as(post("/api/v1/rss-feeds"), owner).content("{\"url\":\"" + base + "/page\"}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("RSS_UNREADABLE"));
    mvc.perform(as(post("/api/v1/rss-feeds"), owner).content("{\"url\":\"\"}"))
        .andExpect(status().isBadRequest());

    String created =
        body(
            mvc.perform(
                    as(post("/api/v1/rss-feeds"), owner).content("{\"url\":\"" + base + "/rss\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.autoPublish").value(false)));
    String id = JsonPath.read(created, "$.id");
    mvc.perform(as(post("/api/v1/rss-feeds"), owner).content("{\"url\":\"" + base + "/rss\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("RSS_DUPLICATE"));

    String other = PublicId.generate();
    mvc.perform(
            as(put("/api/v1/rss-feeds/{id}", id), other)
                .content("{\"autoPublish\":true,\"enabled\":true}"))
        .andExpect(status().isNotFound());
    mvc.perform(as(get("/api/v1/rss-feeds"), other))
        .andExpect(jsonPath("$.items.length()").value(0));
  }

  private int conversations(String owner) {
    return jdbc.queryForObject(
        "select count(*) from conversations where owner_id = ?", Integer.class, owner);
  }

  private int made(String feedId) {
    return jdbc.queryForObject(
        "select count(*) from rss_feed_items i join rss_feeds f on f.id = i.feed_id"
            + " where f.public_id = ? and i.status = 'MADE' and i.conversation_id is not null",
        Integer.class,
        feedId);
  }

  private static void await(BooleanSupplier done) throws InterruptedException {
    long until = System.currentTimeMillis() + 30_000;
    while (!done.getAsBoolean()) {
      if (System.currentTimeMillis() > until) {
        throw new AssertionError("시간 안에 끝나지 않았다");
      }
      Thread.sleep(100);
    }
  }

  private static String body(org.springframework.test.web.servlet.ResultActions result)
      throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }

  private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder r, String user) {
    return r.header("X-User-Id", user).contentType(MediaType.APPLICATION_JSON);
  }
}
