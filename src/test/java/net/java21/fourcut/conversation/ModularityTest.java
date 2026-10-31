package net.java21.fourcut.conversation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

  @Test
  @DisplayName("모듈 사이에 순환 의존이 없고 내부 타입을 넘나들지 않는다")
  void verify() {
    ApplicationModules.of(ConversationApplication.class).verify();
  }
}
