package net.java21.fourcut.conversation.pipeline;

import java.time.Duration;
import java.util.Map;
import net.java21.fourcut.conversation.storage.ObjectStorage;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 작업 입력의 객체 키를 가져갈 때 사전 서명 주소로 바꾼다. {@code xGetKey} → {@code xUrl}(GET), {@code xGetKeys} → {@code
 * xUrls}(GET 목록), {@code xPutKey} → {@code xUrl}(PUT). 워커는 MinIO 키를 갖지 않는다.
 */
@Component
class PayloadResolver {

  private static final String GET_KEYS = "GetKeys";
  private static final String GET_KEY = "GetKey";
  private static final String PUT_KEY = "PutKey";

  private final ObjectStorage storage;
  private final JsonMapper json;

  PayloadResolver(ObjectStorage storage, JsonMapper json) {
    this.storage = storage;
    this.json = json;
  }

  JsonNode resolve(String payload, Duration ttl) {
    return resolve(json.readTree(payload), ttl);
  }

  private JsonNode resolve(JsonNode node, Duration ttl) {
    if (node.isArray()) {
      ArrayNode out = json.createArrayNode();
      node.forEach(n -> out.add(resolve(n, ttl)));
      return out;
    }
    if (!node.isObject()) {
      return node;
    }
    ObjectNode out = json.createObjectNode();
    for (Map.Entry<String, JsonNode> field : node.properties()) {
      String name = field.getKey();
      JsonNode value = field.getValue();
      if (name.endsWith(GET_KEYS) && value.isArray()) {
        ArrayNode urls = out.putArray(base(name, GET_KEYS) + "Urls");
        value.forEach(k -> urls.add(storage.presignGet(k.asString(), ttl)));
      } else if (name.endsWith(GET_KEY) && value.isString()) {
        out.put(base(name, GET_KEY) + "Url", storage.presignGet(value.asString(), ttl));
      } else if (name.endsWith(PUT_KEY) && value.isString()) {
        out.put(base(name, PUT_KEY) + "Url", storage.presignPut(value.asString(), null, ttl));
      } else {
        out.set(name, resolve(value, ttl));
      }
    }
    return out;
  }

  private static String base(String name, String suffix) {
    return name.substring(0, name.length() - suffix.length());
  }
}
