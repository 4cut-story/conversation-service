package net.java21.fourcut.conversation.scenario;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 라이브 모드의 움직임 (docs specs/003-live-mode). 넓은 그림 컷은 네 장, 정사각형 컷은 두 장이다. 모두 배경만 바뀌는 움직임이다. 인물이 움직여야
 * 하는 컷은 고르지 않는다. 콘티 LLM이 이 목록에서 고르고, 코드가 그 배경 낱말이 컷 배경(setting)에 있는지 다시 본다. 첫 그림 프롬프트는 바꾸지 않고 두 번째
 * 그림 edit에만 {@link #change}를 쓴다.
 */
public enum Motion {
  /** 별빛·작은 불빛 반짝임. 밤하늘과 다른 별, 그림 밝기는 그대로 두고 별 몇 개만 밝힌다. */
  TWINKLE(
      "star|stars|starry|starlight|fairy lights|city lights|string lights",
      false,
      700,
      "make a few of the stars and small lights a little brighter, with tiny star-shaped"
          + " glints. Keep the dark night sky, every other star and the brightness of the"
          + " picture exactly the same",
      "make a different few of the stars and small lights a little brighter. Keep the dark"
          + " night sky, every other star and the brightness of the picture exactly the same",
      "make yet another few of the stars and small lights a little brighter. Keep the dark"
          + " night sky, every other star and the brightness of the picture exactly the same"),
  /** 빗줄기. 야외만. */
  RAIN(
      "rain|raining|rainy|raindrops",
      true,
      250,
      "redraw only the falling rain streaks in slightly different places, the same amount of"
          + " rain",
      "redraw only the falling rain streaks in other different places, the same amount of rain,"
          + " with small splashes on the ground",
      "redraw only the falling rain streaks in new places again, the same amount of rain, with"
          + " small ripples in the puddles"),
  /** 내리는 눈. 야외만. */
  SNOW(
      "snow|snowing|snowy|snowflakes",
      true,
      600,
      "redraw only the falling snowflakes in slightly different places, the same amount of snow",
      "redraw only the falling snowflakes in other different places, a little lower, the same"
          + " amount of snow",
      "redraw only the falling snowflakes in new places again, a little lower, the same amount"
          + " of snow, a few of them larger"),
  /** 촛불·등불·모닥불 불꽃 흔들림. 방 밝기는 그대로 두고 불꽃 모양만 바꾼다. */
  FLICKER(
      "candle|candles|candlelight|lantern|lanterns|neon|campfire|fireplace|torch|bonfire",
      false,
      350,
      "change only the shape of the flames a little, leaning slightly to one side. Keep the"
          + " brightness and colors of the room and everything else exactly the same",
      "change only the shape of the flames a little, leaning slightly to the other side and a"
          + " little taller. Keep the brightness and colors of the room and everything else exactly"
          + " the same",
      "change only the shape of the flames a little, a little shorter and wider. Keep the"
          + " brightness and colors of the room and everything else exactly the same"),
  /** 물결 반짝임. */
  SHIMMER(
      "sea|ocean|lake|river|pond|water|waves|beach",
      false,
      800,
      "add bright sparkling highlights on the water surface",
      "add bright sparkling highlights on other places of the water surface, along the"
          + " ripples",
      "add soft glinting highlights on yet other places of the water surface, with small"
          + " gentle waves"),
  /** 떨어지는 꽃잎·낙엽. */
  PETALS(
      "petals|blossom|blossoms|cherry blossom|leaves|autumn leaves|falling leaves",
      false,
      700,
      "redraw only the falling petals and leaves in the air in slightly different places",
      "redraw only the falling petals and leaves in the air in other different places, a few of"
          + " them closer to the ground",
      "redraw only the falling petals and leaves in the air in new places again, a few of them"
          + " drifting sideways"),
  /** 맞는 움직임이 없는 컷의 가벼운 바람(FR-049). 하늘·날씨·색·빛은 그대로 두고 잎·풀·천만 흔든다. 어느 배경에나 맞는다. */
  BREEZE(
      "\\w+",
      false,
      900,
      "let the leaves, grass and fabric in the background sway slightly as if in a light"
          + " breeze. Keep the colors and the lighting exactly the same, and keep any rain"
          + " streaks or snowflakes as they are",
      "let the leaves, grass and fabric in the background sway slightly to the other side as if"
          + " in a light breeze. Keep the colors and the lighting exactly the same, and keep any"
          + " rain streaks or snowflakes as they are",
      "let the leaves, grass and fabric in the background sway gently back as if in a light"
          + " breeze. Keep the colors and the lighting exactly the same, and keep any rain"
          + " streaks or snowflakes as they are");

  /** 한 만화에 움직이는 컷 최대 수(컷 수와 같다, FR-049). 움직이는 넓은 그림 컷마다 GPU 그림이 세 장 더 든다. */
  public static final int MAX_PER_COMIC = 10;

  /** 실내 낱말. 실내 컷의 비·눈은 그리지 않는다(콘티 규칙상 실내 setting에 rain을 쓰지 않는다). */
  private static final Pattern INDOOR =
      Pattern.compile(
          "\\b(room|bedroom|kitchen|inside|indoors?|office|cafe|café|shop|store|classroom|window|"
              + "living room|restaurant|hallway)\\b");

  private final Pattern background;
  private final boolean outdoorOnly;
  private final int intervalMs;
  private final String change;
  private final String next;
  private final String last;

  Motion(
      String background,
      boolean outdoorOnly,
      int intervalMs,
      String change,
      String next,
      String last) {
    this.background = Pattern.compile("\\b(" + background + ")\\b");
    this.outdoorOnly = outdoorOnly;
    this.intervalMs = intervalMs;
    this.change = change;
    this.next = next;
    this.last = last;
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

  /** 세 번째 그림에서 바꿀 것. 두 번째 그림과 다른 자리·다른 세기라 세 장을 차례로 보여 주면 더 움직여 보인다 (FR-044). */
  public String next() {
    return next;
  }

  /** 네 번째 그림에서 바꿀 것 (FR-049). 넓은 그림 컷만 쓴다. */
  public String last() {
    return last;
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

  /**
   * 배경 낱말로 고르는 차례. 날씨처럼 화면을 크게 차지하는 것부터, 끝은 BREEZE. 별빛은 물결보다 앞이다: 옥상 밤하늘 배경에 멀리 강이 함께 쓰이면 물결이 골라져
   * 하늘이 회색으로 다시 그려졌다.
   */
  private static final List<Motion> FALLBACK =
      List.of(RAIN, SNOW, PETALS, FLICKER, TWINKLE, SHIMMER, BREEZE);

  /**
   * 맞는 움직임이 없는 컷에 배경 문구로 움직임을 고른다(FR-049). 비 오는 컷에 바람을 쓰면 빗줄기가 지워지고 다른 것이 흔들려 번갈아 볼 때 비가 깜빡이므로, 배경에
   * 맞는 움직임을 먼저 쓰고 없으면 BREEZE다.
   */
  public static Optional<Motion> fallback(String sceneAndSetting) {
    return FALLBACK.stream().filter(m -> m.fits(sceneAndSetting)).findFirst();
  }

  /** 콘티 지시에 넣는 목록 줄. BREEZE는 코드가 채운다. */
  static List<String> codes() {
    return java.util.Arrays.stream(values()).filter(m -> m != BREEZE).map(Motion::code).toList();
  }
}
