package net.java21.fourcut.conversation.character;

import java.util.Optional;

/** 글 → 설명 임베딩 (768차원, 길이 1로 맞춘다). 쓸 수 없으면 빈 값이고, 그동안 캐릭터는 이름으로만 찾는다. */
interface TextEmbedder {

  Optional<float[]> embed(String text);

  /** 임베딩 모델 이름 (characters.embedding_model). */
  String model();
}
