package com.tongrui.study.controller;

import com.tongrui.study.service.FavoriteService;
import com.tongrui.study.service.WrongQuestionService;
import com.tongrui.study.vo.PageVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PersonalQuestionFilterValidationTests {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private WrongQuestionService wrongQuestionService;
    @MockitoBean private FavoriteService favoriteService;

    @MockitoBean private com.tongrui.study.service.PracticeService practiceService;
    @MockitoBean private com.tongrui.study.service.PersonalQuestionExcelExportService exportService;

    private UsernamePasswordAuthenticationToken student;

    @BeforeEach
    void setUp() {
        student = new UsernamePasswordAuthenticationToken(
                7L, null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
    }

    @Test
    void keywordLengthIsValidatedByBothControllers() throws Exception {
        String keyword = "x".repeat(101);

        mockMvc.perform(get("/api/wrong-questions").param("keyword", keyword)
                        .with(authentication(student)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/favorites").param("keyword", keyword)
                        .with(authentication(student)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void wrongCountOrderRejectsValuesOutsideWhitelist() throws Exception {
        mockMvc.perform(get("/api/wrong-questions").param("wrongCountOrder", "recent")
                        .with(authentication(student)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void favoriteTimeOrderIsValidatedAcrossListPracticeAndExport() throws Exception {
        for (String path : List.of("/api/favorites", "/api/practice/questions", "/api/favorites/export")) {
            mockMvc.perform(get(path).param("source", "FAVORITE").param("favoriteTimeOrder", "SQL")
                    .with(authentication(student))).andExpect(status().isBadRequest());
        }
        when(favoriteService.page(1, 10, 7L, null, null, null, null, null, null, "ASC"))
                .thenReturn(new PageVO<>(List.of(), 0, 1, 10, 0));
        mockMvc.perform(get("/api/favorites").param("favoriteTimeOrder", "ASC").param("userId", "999")
                .with(authentication(student))).andExpect(status().isOk());
        verify(favoriteService).page(1, 10, 7L, null, null, null, null, null, null, "ASC");
    }

    @Test
    void practiceAndExportRejectInvalidFilters() throws Exception {
        for (String path : List.of("/api/practice/questions", "/api/wrong-questions/export", "/api/favorites/export")) {
            for (String id : List.of("subjectId", "chapterId", "knowledgePointId")) {
                mockMvc.perform(get(path).param("source", "WRONG").param(id, "-1")
                        .with(authentication(student))).andExpect(status().isBadRequest());
            }
            mockMvc.perform(get(path).param("source", "WRONG").param("keyword", "x".repeat(101))
                    .with(authentication(student))).andExpect(status().isBadRequest());
            mockMvc.perform(get(path).param("source", "WRONG").param("wrongCountOrder", "SQL")
                    .with(authentication(student))).andExpect(status().isBadRequest());
        }
    }

    @Test
    void practiceIgnoresForgedUserId() throws Exception {
        when(practiceService.listQuestions(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.tongrui.study.vo.PracticeQuestionListVO(
                        com.tongrui.study.enums.PracticeSource.WRONG, null, "错题", List.of()));
        mockMvc.perform(get("/api/practice/questions").param("source", "WRONG").param("userId", "999")
                .with(authentication(student))).andExpect(status().isOk());
        verify(practiceService).listQuestions(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void validFiltersAreForwardedWithoutAcceptingAUserId() throws Exception {
        when(wrongQuestionService.page(1, 10, 7L, 1L, 2L, 3L,
                null, null, "线性表", "DESC"))
                .thenReturn(new PageVO<>(List.of(), 0, 1, 10, 0));

        mockMvc.perform(get("/api/wrong-questions")
                        .param("subjectId", "1")
                        .param("chapterId", "2")
                        .param("knowledgePointId", "3")
                        .param("keyword", "线性表")
                        .param("wrongCountOrder", "DESC")
                        .with(authentication(student)))
                .andExpect(status().isOk());

        verify(wrongQuestionService).page(1, 10, 7L, 1L, 2L, 3L,
                null, null, "线性表", "DESC");
    }
}
