package net.java21.fourcut.conversation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.modulith.Modulithic;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 대화 → 4컷 만화. 모듈: conversations(API), pipeline(엔진·GPU 작업 대기열), stt, scenario, comic. 모듈 경계는 Spring
 * Modulith가 테스트에서 확인한다.
 */
@Modulithic(sharedModules = {"error", "web", "support", "storage"})
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class ConversationApplication {

  public static void main(String[] args) {
    SpringApplication.run(ConversationApplication.class, args);
  }
}
