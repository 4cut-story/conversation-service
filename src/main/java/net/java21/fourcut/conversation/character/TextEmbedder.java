package net.java21.fourcut.conversation.character;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 글 → 설명 임베딩 (BGE-M3 1024차원, 길이 1로 맞춘다). 쓸 수 없으면 빈 값이다. 캐릭터와 배경음(sound) 검색이 함께 쓴다. */
public interface TextEmbedder {

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
