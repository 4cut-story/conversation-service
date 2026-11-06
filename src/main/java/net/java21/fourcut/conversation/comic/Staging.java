package net.java21.fourcut.conversation.comic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.java21.fourcut.conversation.scenario.ComicScript;

/**
 * 한 만화 안에서 인물이 서는 자리. 두 명이 나오는 컷은 인물마다 정해 둔 쪽(왼쪽·오른쪽)에 세운다. 그림(IP-Adapter 마스크, 프롬프트 위치 문구)과 말풍선(화자
 * 쪽에 두고 꼬리로 가리킴)이 같은 자리를 쓴다.
 *
 * <p>예전에는 장면 문장에 먼저 나온 인물을 왼쪽에 두어, 문장 순서가 바뀌면 컷마다 좌우가 바뀌었다. 이제는 가장 많은 컷에 나오는 두 인물(같으면 등장인물 순서)을 골라
 * 등장인물 순서대로 왼쪽·오른쪽에 고정한다. 고정 자리가 없는 인물은 같은 컷의 다른 인물 반대쪽에 서고, 세 명 이상 나오는 컷은 말하는 인물이 먼저 왼쪽·오른쪽에 서고 셋째
 * 인물부터는 가운데다. 혼자 나오는 컷은 가운데다.
 */
final class Staging {

  enum Side {
    LEFT,
    RIGHT,
    CENTER
  }

  private Staging() {}

  /** 인물별 고정 자리. 컷에 나오는 횟수가 많은 두 인물만 정한다. */
  static Map<String, Side> home(ComicScript script) {
    Map<String, Integer> count = new HashMap<>();
    for (ComicScript.Panel p : script.panels()) {
      for (String who : DrawHandler.people(script, p)) {
        count.merge(who, 1, Integer::sum);
      }
    }
    List<String> order = new ArrayList<>();
    script.characters().forEach(c -> order.add(c.speaker()));
    List<String> ranked =
        order.stream()
            .filter(count::containsKey)
            .sorted(Comparator.comparingInt((String s) -> -count.get(s)))
            .toList();
    if (ranked.size() < 2) {
      // 컷에 한 명뿐이거나 장면에 인물 표시가 없으면 등장인물 순서
      ranked = order;
    }
    // 고른 두 인물은 등장인물 순서대로 왼쪽·오른쪽 (보통 먼저 말한 인물이 왼쪽)
    List<String> pair =
        order.stream().filter(ranked.subList(0, Math.min(2, ranked.size()))::contains).toList();
    Map<String, Side> home = new HashMap<>();
    if (!pair.isEmpty()) {
      home.put(pair.get(0), Side.LEFT);
    }
    if (pair.size() > 1) {
      home.put(pair.get(1), Side.RIGHT);
    }
    return home;
  }

  /** 컷에 나오는 인물의 자리. 두 명 이상이면 왼쪽, 오른쪽, 그다음 가운데 인물 순서다. */
  static Map<String, Side> panel(ComicScript script, ComicScript.Panel panel) {
    return panel(script, panel, home(script));
  }

  static Map<String, Side> panel(
      ComicScript script, ComicScript.Panel panel, Map<String, Side> home) {
    // 세 명 이상일 때 왼쪽·오른쪽(참조 그림이 붙는 자리)은 이 컷에서 말하는 인물이 먼저 차지하고, 그다음 고정 자리가 있는
    // 인물이다. 말하는 인물이 가운데로 밀리면 참조 없이 그려져 컷에서 사라졌다(4컷에서 정체를 밝히는 할머니가 빠졌다).
    Set<String> talking = new HashSet<>();
    if (panel.lines() != null) {
      panel.lines().forEach(l -> talking.add(l.speaker()));
    }
    List<String> people =
        DrawHandler.people(script, panel).stream()
            .sorted(
                Comparator.comparingInt(
                    (String s) -> (talking.contains(s) ? 0 : 2) + (home.containsKey(s) ? 0 : 1)))
            .toList();
    Map<String, Side> out = new LinkedHashMap<>();
    if (people.isEmpty()) {
      return out;
    }
    if (people.size() == 1) {
      out.put(people.get(0), Side.CENTER);
      return out;
    }
    String a = people.get(0);
    String b = people.get(1);
    Side sa = home.get(a);
    Side sb = home.get(b);
    boolean swap;
    if (sa != null) {
      swap = sa == Side.RIGHT;
    } else if (sb != null) {
      swap = sb == Side.LEFT;
    } else {
      swap = false;
    }
    String left = swap ? b : a;
    String right = swap ? a : b;
    out.put(left, Side.LEFT);
    out.put(right, Side.RIGHT);
    // 셋째 인물부터는 가운데 (말풍선 꼬리용. IP-Adapter 참조는 왼쪽·오른쪽 두 명까지)
    people.stream().skip(2).forEach(p -> out.put(p, Side.CENTER));
    return out;
  }

  /** 모든 컷의 자리. */
  static List<Map<String, Side>> panels(ComicScript script) {
    Map<String, Side> home = home(script);
    return script.panels().stream().map(p -> panel(script, p, home)).toList();
  }
}
