package net.java21.fourcut.conversation.article;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import net.java21.fourcut.conversation.storage.ObjectStorage;
import net.java21.fourcut.conversation.storage.WorkKeys;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 골라낸 기사 본문을 대화의 작업 폴더(pipeline-work, 7일 보관)에 둔다. 대화를 지우면 함께 지워진다. */
@Component
public class ArticleStore {

  private final ObjectStorage storage;
  private final JsonMapper json;

  public ArticleStore(ObjectStorage storage, JsonMapper json) {
    this.storage = storage;
    this.json = json;
  }

  public void save(String conversationId, Article article) {
    storage.put(
        WorkKeys.article(conversationId),
        json.writeValueAsString(article).getBytes(StandardCharsets.UTF_8),
        "application/json");
  }

  public Article load(String conversationId) {
    byte[] bytes =
        storage
            .get(WorkKeys.article(conversationId))
            .orElseThrow(() -> new IllegalStateException("기사 본문이 없다"));
    return json.readValue(bytes, Article.class);
  }

  /** 각색 기획을 둔다. */
  public void savePlan(String conversationId, ArticleDialogue.Plan plan) {
    storage.put(
        WorkKeys.articlePlan(conversationId),
        json.writeValueAsString(plan).getBytes(StandardCharsets.UTF_8),
        "application/json");
  }

  /** 각색 기획. 녹음으로 만든 대화나 기획이 없는 옛 기사 대화는 빈 값. */
  public Optional<ArticleDialogue.Plan> plan(String conversationId) {
    return storage
        .get(WorkKeys.articlePlan(conversationId))
        .map(bytes -> json.readValue(bytes, ArticleDialogue.Plan.class));
  }
}
