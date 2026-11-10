package net.java21.fourcut.conversation.scenario;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 라이브 모드 화면 효과 층 (docs specs/003-live-mode FR-040~). 그림 위에 화면이 그리는 작은 움직임이라 GPU를 쓰지 않는다. 콘티 LLM이
 * 컷마다 이 목록에서 고르고, 코드가 그 배경 낱말이 컷 장면·배경(setting)에 있는지 다시 본다. 그림에 없는 것을 띄우지 않기 위해서다.
 */
public enum Ambient {
  /** 별빛·작은 불빛 반짝임. */
  TWINKLE("star|stars|starry|starlight|night sky|fairy lights|string lights|city lights", false),
  /** 빗줄기. 야외만. */
  RAIN("rain|raining|rainy|raindrops|downpour|drizzle", true),
  /** 내리는 눈. 야외만. */
  SNOW("snow|snowing|snowy|snowflakes|snowfall", true),
  /** 반딧불이. */
  FIREFLIES("firefly|fireflies", false),
  /** 흩날리는 꽃잎. */
  PETALS("petals|blossom|blossoms|cherry blossom|cherry blossoms", false),
  /** 떨어지는 낙엽. */
  LEAVES("autumn leaves|falling leaves|fallen leaves|autumn", false),
  /** 뜨거운 음식·음료·목욕물 김. */
  STEAM(
      "steam|steaming|coffee|tea|ramen|noodles|soup|hot pot|stew|kettle|bath|sauna|hot spring",
      false),
  /** 창으로 든 햇빛 속 먼지. */
  DUST("sunbeam|sunbeams|sunlight|sunlit|light rays|dusty|attic", false),
  /** 촛불·등불·모닥불·네온 깜빡임. */
  FLICKER(
      "candle|candles|candlelight|lantern|lanterns|neon|campfire|fireplace|torch|bonfire", false),
  /** 별똥별이 하늘을 가로질러 떨어진다. */
  METEOR(
      "shooting star|shooting stars|falling star|falling stars|meteor|meteors|meteor shower",
      false),
  /** 불꽃놀이가 터진다. */
  FIREWORKS("firework|fireworks", false),
  /** 번개가 번쩍인다. */
  LIGHTNING("lightning|thunder|thunderstorm", false),
  /** 창으로 드는 빛줄기가 천천히 밝아졌다 옅어진다. 밤 장면은 아니다. */
  SUNBEAM("window|windows|daylight|morning light|skylight|sunny", false, Ambient.NIGHT),
  /** 모니터·텔레비전 화면 빛이 은은하게 깜빡인다. */
  SCREEN(
      "monitor|monitors|screen|screens|computer|computers|laptop|laptops|tv|television|"
          + "broadcast|newsroom|control room",
      false),
  /** 멀리 도시 불빛·자동차 불빛이 흐릿한 빛 동그라미로 번진다. */
  BOKEH(
      "street lights|streetlights|street lamps|streetlamps|headlights|taillights|traffic|"
          + "neon signs|night city|downtown|cars|city street",
      false),
  /** 하늘에 구름이 천천히 흐른다. 야외 낮만. */
  CLOUDS("cloud|clouds|cloudy|blue sky|sky|skyline|rooftop|field|park", true, Ambient.NIGHT),
  /** 비눗방울·수족관 거품이 떠오른다. */
  BUBBLES("bubble|bubbles|soap|aquarium|fish tank|bathtub", false),
  /** 은은하게 떠다니는 빛 알갱이. 배경과 상관없이 어울려서, 다른 효과가 없는 컷에 코드가 넣는다. */
  GLOW("", false);

  /** 밤 낱말. 창빛·구름은 밤 장면에 띄우지 않는다. */
  private static final String NIGHT = "night|midnight|dark|starry|moonlight|moonlit";

  /** 한 컷에 겹치는 효과 최대 수. 많으면 그림이 가린다. */
  public static final int MAX_PER_PANEL = 2;

  /** 실내 낱말. 실내 컷의 비·눈은 띄우지 않는다. */
  private static final Pattern INDOOR =
      Pattern.compile(
          "\\b(room|bedroom|kitchen|inside|indoors?|office|cafe|café|shop|store|classroom|window|"
              + "living room|restaurant|hallway)\\b");

  private final Pattern background;
  private final boolean outdoorOnly;
  private final Pattern avoid;

  Ambient(String background, boolean outdoorOnly) {
    this(background, outdoorOnly, null);
  }

  Ambient(String background, boolean outdoorOnly, String avoid) {
    this.background = background.isEmpty() ? null : Pattern.compile("\\b(" + background + ")\\b");
    this.outdoorOnly = outdoorOnly;
    this.avoid = avoid == null ? null : Pattern.compile("\\b(" + avoid + ")\\b");
  }

  /** 재생 정보와 LLM 출력에 쓰는 이름(소문자). */
  public String code() {
    return name().toLowerCase(Locale.ROOT);
  }

  /** LLM이 쓴 값. 목록에 없으면 비었다. */
  public static Optional<Ambient> parse(String value) {
    if (value == null) {
      return Optional.empty();
    }
    String v = value.strip().toUpperCase(Locale.ROOT);
    return Arrays.stream(values()).filter(a -> a.name().equals(v)).findFirst();
  }

  /** 이 효과의 배경이 그 컷 장면·배경 문구에 그려지는지. 실내 비·눈은 아니다. */
  public boolean fits(String sceneAndSetting) {
    if (background == null) {
      return true;
    }
    if (sceneAndSetting == null || sceneAndSetting.isBlank()) {
      return false;
    }
    String text = sceneAndSetting.toLowerCase(Locale.ROOT);
    if (!background.matcher(text).find()) {
      return false;
    }
    if (avoid != null && avoid.matcher(text).find()) {
      return false;
    }
    return !outdoorOnly || !INDOOR.matcher(text).find();
  }

  /** LLM이 고른 값을 거른다: 목록 밖·중복·배경에 없는 값은 빼고 앞에서부터 {@link #MAX_PER_PANEL}개만 남긴다. */
  public static List<String> filter(List<String> chosen, String sceneAndSetting) {
    List<String> out = new ArrayList<>();
    if (chosen == null) {
      return out;
    }
    for (String c : chosen) {
      Optional<Ambient> a = parse(c);
      if (a.isPresent()
          && !out.contains(a.get().code())
          && out.size() < MAX_PER_PANEL
          && a.get().fits(sceneAndSetting)) {
        out.add(a.get().code());
      }
    }
    return out;
  }

  /**
   * 효과가 하나도 남지 않은 컷의 효과 (marco: 한 컷만 움직이지 않고 모든 컷에 움직임이 있게). 배경 문구에 근거 낱말이 있는 첫 효과를 쓰고, 없으면 {@link
   * #GLOW}다.
   */
  public static List<String> fallback(String sceneAndSetting) {
    return fallback(sceneAndSetting, List.of());
  }

  /**
   * 앞 컷 효과(previous)와 겹치지 않는 효과를 먼저 고른다. 배경 문구에 맞는 효과가 앞 컷 것뿐이면 그것을 쓰고, 하나도 없으면 {@link #GLOW}다. 여러
   * 편을 이어 보면 은은한 빛·먼지만 되풀이돼 새롭지 않았다(최근 40편 264컷 중 60%).
   */
  public static List<String> fallback(String sceneAndSetting, List<String> previous) {
    List<Ambient> fit =
        Arrays.stream(values()).filter(a -> a != GLOW && a.fits(sceneAndSetting)).toList();
    return fit.stream()
        .filter(a -> previous == null || !previous.contains(a.code()))
        .findFirst()
        .or(() -> fit.stream().findFirst())
        .map(a -> List.of(a.code()))
        .orElse(List.of(GLOW.code()));
  }
}
