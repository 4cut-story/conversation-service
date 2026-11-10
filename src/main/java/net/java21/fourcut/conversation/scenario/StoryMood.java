package net.java21.fourcut.conversation.scenario;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * 이야기 전체 분위기. 배경음 라이브러리(background_sounds.moods)의 분위기 이름과 같다. 콘티 LLM이 하나 고르고, 라이브 모드는 장면에 꼭 맞는
 * 장소·환경음이 없는 컷에 이 분위기의 배경음악을 깐다(marco, 2026-11-10: 우울한 이야기에 환풍구 소리 대신 슬픈 음악).
 */
public enum StoryMood {
  PEACEFUL("평온하고 잔잔한 일상, 여유롭고 따뜻한 하루"),
  JOYFUL("기쁘고 신나는 일, 축하하고 웃는 밝은 이야기"),
  COMIC("웃기고 엉뚱한 해프닝, 장난과 농담이 오가는 코믹한 이야기"),
  EXCITING("두근거리고 신나는 모험, 활기차고 빠른 전개"),
  PARTY("파티와 잔치, 떠들썩하게 노는 흥겨운 자리"),
  ROMANTIC("설레는 사랑, 고백과 데이트, 다정한 연인"),
  NOSTALGIC("그리운 옛 추억, 아련한 회상, 가족과 고향 생각"),
  LONELY("혼자 남은 쓸쓸함, 외롭고 허전한 마음"),
  SAD("슬프고 우울한 일, 억울하고 힘든 처지, 눈물과 상실, 위로가 필요한 이야기"),
  SOLEMN("엄숙하고 진지한 다짐, 무거운 결심, 추모와 이별"),
  TENSE("긴장되고 불안한 순간, 갈등과 다툼, 초조한 기다림"),
  MYSTERIOUS("수상하고 신비로운 일, 알 수 없는 비밀"),
  SCARY("무섭고 오싹한 일, 공포와 섬뜩함"),
  EPIC("웅장하고 벅찬 승리, 큰 도전과 극복");

  private final String anchor;

  StoryMood(String anchor) {
    this.anchor = anchor;
  }

  /** 분위기를 글로 쓴 한 줄. 분위기를 적지 않은 예전 콘티의 줄거리와 견줘 분위기를 짐작할 때 쓴다. */
  public String anchor() {
    return anchor;
  }

  /** LLM이 쓴 값. 목록에 없으면 비었다. */
  public static Optional<StoryMood> parse(String value) {
    if (value == null) {
      return Optional.empty();
    }
    String v = value.strip().toUpperCase(Locale.ROOT);
    return Arrays.stream(values()).filter(m -> m.name().equals(v)).findFirst();
  }
}
