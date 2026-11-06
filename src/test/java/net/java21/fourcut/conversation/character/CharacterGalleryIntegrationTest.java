package net.java21.fourcut.conversation.character;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import net.java21.fourcut.conversation.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** 캐릭터 갤러리 공개 API: 거르기, 커서, 상세 시트 순서, 그림 주소, 꺼진 캐릭터 숨기기 (REQ-018 보기). */
@SpringBootTest(
    properties = {
      "pipeline.recovery-interval=1h",
      "pipeline.local-recovery-interval=1h",
      "DB_SCHEMA=public"
    })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CharacterGalleryIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private StoryCharacterRepository characters;
  @Autowired private CharacterLookRepository looks;

  private StoryCharacter save(String name, String job, String gender, String age) {
    StoryCharacter c =
        characters.save(new StoryCharacter(name, name + " 설명", job, gender, age, 0.6));
    looks.save(new CharacterLook(c, "WUXIA", "SAD", "wuxia sad", "d"));
    looks.save(new CharacterLook(c, "DAILY", "NEUTRAL", "daily", "d"));
    looks.save(new CharacterLook(c, "WUXIA", "NEUTRAL", "wuxia", "d"));
    looks.save(new CharacterLook(c, "DAILY", "HAPPY", "daily happy", "d"));
    return c;
  }

  @Test
  @DisplayName("REQ-018: 갤러리에서 캐릭터를 직업·나이대·이름으로 거르고 상세에서 컨셉×표정 모습을 본다")
  void browse() throws Exception {
    StoryCharacter a = save("갤러리가람", "갤러리화가", "FEMALE", "YOUNG");
    save("갤러리나래", "갤러리화가", "MALE", "OLD");
    StoryCharacter off = save("갤러리다온", "갤러리요리사", "MALE", "CHILD");
    off.deactivate();
    characters.save(off);

    mvc.perform(get("/api/v1/characters").param("job", "갤러리화가").param("concept", "WUXIA"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].name").value("갤러리가람"))
        .andExpect(jsonPath("$.items[0].coverLookId").isNotEmpty())
        .andExpect(jsonPath("$.nextCursor").doesNotExist());

    String first =
        mvc.perform(get("/api/v1/characters").param("q", "갤러리").param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String cursor = JsonPath.read(first, "$.nextCursor");
    mvc.perform(get("/api/v1/characters").param("q", "갤러리").param("cursor", cursor))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].name").value("갤러리나래"));

    mvc.perform(get("/api/v1/characters").param("ageGroup", "CHILD").param("q", "갤러리"))
        .andExpect(jsonPath("$.items.length()").value(0));
    mvc.perform(get("/api/v1/characters").param("concept", "MARS"))
        .andExpect(status().isBadRequest());

    mvc.perform(get("/api/v1/characters/jobs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.job=='갤러리화가')].count").value(2))
        .andExpect(jsonPath("$[?(@.job=='갤러리요리사')]").isEmpty());

    String detail =
        mvc.perform(get("/api/v1/characters/" + a.publicId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.job").value("갤러리화가"))
            .andExpect(jsonPath("$.looks.length()").value(4))
            .andExpect(jsonPath("$.looks[0].concept").value("DAILY"))
            .andExpect(jsonPath("$.looks[0].expression").value("NEUTRAL"))
            .andExpect(jsonPath("$.looks[1].expression").value("HAPPY"))
            .andExpect(jsonPath("$.looks[3].expression").value("SAD"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String lookId = JsonPath.read(detail, "$.looks[0].id");
    mvc.perform(get("/api/v1/characters/looks/" + lookId + "/image"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").isNotEmpty());

    mvc.perform(get("/api/v1/characters/" + off.publicId())).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/characters/nope")).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/characters/looks/nope/image")).andExpect(status().isNotFound());
  }
}
