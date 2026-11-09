package net.java21.fourcut.conversation.scenario;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 라이브 모드의 두 장 움직임 (docs specs/003-live-mode). 모두 배경만 바뀌는 움직임이다. 인물이 움직여야 하는 컷은 고르지 않는다. 콘티 LLM이 이
 * 목록에서 고르고, 코드가 그 배경 낱말이 컷 배경(setting)에 있는지 다시 본다. 첫 그림 프롬프트는 바꾸지 않고 두 번째 그림 edit에만 {@link
 * #change}를 쓴다.
 */
public enum Motion {
  /** 별빛·작은 불빛 반짝임. */
  TWINKLE(
      "star|stars|starry|starlight|fairy lights|city lights|string lights",
      false,
      700,
      "make the stars and small lights in the background sparkle brighter, with small"
          + " star-shaped glints"),
  /** 빗줄기. 야외만. */
  RAIN(
      "rain|raining|rainy|raindrops",
      true,
      250,
      "redraw only the falling rain streaks in slightly different places, the same amount of"
          + " rain"),
  /** 내리는 눈. 야외만. */
  SNOW(
      "snow|snowing|snowy|snowflakes",
      true,
      600,
      "redraw only the falling snowflakes in slightly different places, the same amount of snow"),
  /** 촛불·등불·네온 깜빡임. */
  FLICKER(
      "candle|candles|candlelight|lantern|lanterns|neon|campfire|fireplace|torch|bonfire",
      false,
      350,
      "make the flame and lamp light glow brighter and warmer, with a soft halo of light around"
          + " it"),
  /** 물결 반짝임. */
  SHIMMER(
      "sea|ocean|lake|river|pond|water|waves|beach",
      false,
      800,
      "add bright sparkling highlights on the water surface"),
  /** 떨어지는 꽃잎·낙엽. */
  PETALS(
      "petals|blossom|blossoms|cherry blossom|leaves|autumn leaves|falling leaves",
      false,
      700,
      "redraw only the falling petals and leaves in the air in slightly different places");

  /** 한 만화에 움직이는 컷 최대 수. 움직이는 컷마다 GPU 그림이 한 장 더 든다. */
  public static final int MAX_PER_COMIC = 2;

  /** 실내 낱말. 실내 컷의 비·눈은 그리지 않는다(콘티 규칙상 실내 setting에 rain을 쓰지 않는다). */
  private static final Pattern INDOOR =
      Pattern.compile(
          "\\b(room|bedroom|kitchen|inside|indoors?|office|cafe|café|shop|store|classroom|window|"
              + "living room|restaurant|hallway)\\b");

  private final Pattern background;
  private final boolean outdoorOnly;
  private final int intervalMs;
  private final String change;

  Motion(String background, boolean outdoorOnly, int intervalMs, String change) {
    this.background = Pattern.compile("\\b(" + background + ")\\b");
    this.outdoorOnly = outdoorOnly;
    this.intervalMs = intervalMs;
    this.change = change;
  }

  /** 재생 정보와 LLM 출력에 쓰는 이름(소문자). */
  public String code() {
    return name().toLowerCase(Locale.ROOT);
  }

  /** 두 그림을 번갈아 보여 주는 간격. */
  public int intervalMs() {
    return intervalMs;
  }

  /** 두 번째 그림에서 바꿀 것 (edit 프롬프트 끝). */
  public String change() {
    return change;
  }

  /** LLM이 쓴 값. 목록에 없거나 none·빈 값이면 비었다. */
  public static Optional<Motion> parse(String value) {
    if (value == null) {
      return Optional.empty();
    }
    String v = value.strip().toUpperCase(Locale.ROOT);
    for (Motion m : values()) {
      if (m.name().equals(v)) {
        return Optional.of(m);
      }
    }
    return Optional.empty();
  }

  /** 이 움직임의 배경이 그 컷 배경 문구(장면·배경)에 그려지는지. 실내 비·눈은 아니다. */
  public boolean fits(String sceneAndSetting) {
    if (sceneAndSetting == null || sceneAndSetting.isBlank()) {
      return false;
    }
    String text = sceneAndSetting.toLowerCase(Locale.ROOT);
    if (!background.matcher(text).find()) {
      return false;
    }
    return !outdoorOnly || !INDOOR.matcher(text).find();
  }

  /** 콘티 지시에 넣는 목록 줄. */
  static List<String> codes() {
    return java.util.Arrays.stream(values()).map(Motion::code).toList();
  }
}
