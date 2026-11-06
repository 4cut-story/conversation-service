package net.java21.fourcut.conversation.character;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 글 → 설명 임베딩 (1024차원, 길이 1로 맞춘다). 쓸 수 없으면 빈 값이고, 그동안 캐릭터는 이름으로만 찾는다. */
interface TextEmbedder {

  Optional<float[]> embed(String text);

  /** 여러 글을 한 번에 임베딩한다. 쓸 수 없으면 빈 목록이고, 아니면 글과 같은 순서·개수다. */
  default List<float[]> embedAll(List<String> texts) {
    List<float[]> out = new ArrayList<>(texts.size());
    for (String t : texts) {
      Optional<float[]> v = embed(t);
      if (v.isEmpty()) {
        return List.of();
      }
      out.add(v.get());
    }
    return out;
  }

  /** 임베딩 모델 이름 (character_looks.embedding_model). */
  String model();
}
