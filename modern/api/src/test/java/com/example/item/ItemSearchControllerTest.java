package com.example.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ItemSearchController.class)
@ActiveProfiles("test")
class ItemSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ItemSearchService itemSearchService;

    @Test
    @DisplayName("GET /api/items/search → 200, {rows, count, message, warnings} 이고 행에 stem · status 가 없다")
    void searchReturnsNormalizedShape() throws Exception {
        when(itemSearchService.search(any())).thenReturn(new ItemSearchResponse(
            List.of(new ItemSearchItemResponse(12, "대분수의 덧셈", "M5-1", 3, List.of("계산", "오답률높음"))),
            1, null, List.of()));

        mockMvc.perform(get("/api/items/search").param("unit", "M5-1").param("level", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(1))
            .andExpect(jsonPath("$.message").doesNotExist())
            .andExpect(jsonPath("$.warnings.length()").value(0))
            .andExpect(jsonPath("$.rows[0].id").value(12))
            .andExpect(jsonPath("$.rows[0].title").value("대분수의 덧셈"))
            .andExpect(jsonPath("$.rows[0].unit").value("M5-1"))
            .andExpect(jsonPath("$.rows[0].level").value(3))
            .andExpect(jsonPath("$.rows[0].tags[1]").value("오답률높음"))
            .andExpect(jsonPath("$.rows[0].stem").doesNotExist())
            .andExpect(jsonPath("$.rows[0].status").doesNotExist());
    }

    @Test
    @DisplayName("0건이면 404 가 아니라 200 과 안내 문구 · 경고")
    void searchWithNoResultIsStillOk() throws Exception {
        when(itemSearchService.search(any())).thenReturn(new ItemSearchResponse(
            List.of(), 0, "검색 결과가 없습니다", List.of("등록되지 않은 단원 코드입니다: Z9-9")));

        mockMvc.perform(get("/api/items/search").param("unit", "Z9-9"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(0))
            .andExpect(jsonPath("$.rows.length()").value(0))
            .andExpect(jsonPath("$.message").value("검색 결과가 없습니다"))
            .andExpect(jsonPath("$.warnings[0]").value("등록되지 않은 단원 코드입니다: Z9-9"));
    }

    @Test
    @DisplayName("파라미터가 없으면 빈 문자열, page 는 \"1\" 로 넘긴다")
    void missingParamsBecomeLegacyDefaults() throws Exception {
        when(itemSearchService.search(any())).thenReturn(new ItemSearchResponse(List.of(), 0, null, List.of()));

        mockMvc.perform(get("/api/items/search")).andExpect(status().isOk());

        ItemSearchCondition condition = captureCondition();
        assertThat(condition).isEqualTo(new ItemSearchCondition("", "", "", "", "", "", "1"));
    }

    @Test
    @DisplayName("같은 이름 반복은 마지막 값, name[] 배열은 첫 값(PHP $_GET 규칙)")
    void repeatedAndArrayParamsFollowPhpRules() throws Exception {
        when(itemSearchService.search(any())).thenReturn(new ItemSearchResponse(List.of(), 0, null, List.of()));

        mockMvc.perform(get("/api/items/search")
                .param("q", "처음", "나중")
                .param("unit[]", "M5-1", "M6-1"))
            .andExpect(status().isOk());

        ItemSearchCondition condition = captureCondition();
        assertThat(condition.q()).isEqualTo("나중");
        assertThat(condition.unit()).isEqualTo("M5-1");
    }

    private ItemSearchCondition captureCondition() {
        ArgumentCaptor<ItemSearchCondition> captor = ArgumentCaptor.forClass(ItemSearchCondition.class);
        verify(itemSearchService).search(captor.capture());
        return captor.getValue();
    }
}
