package com.tongrui.study.security;

import com.tongrui.study.service.QuestionImportService;
import com.tongrui.study.vo.ExcelExportFile;
import com.tongrui.study.vo.QuestionImportResult;
import com.tongrui.study.vo.QuestionImportValidationResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ExportSecurityTests {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private QuestionImportService questionImportService;

    @Test
    void anonymousCannotExportLearningStatistics() throws Exception {
        mockMvc.perform(get("/api/statistics/export")).andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousCannotExportQuestionBank() throws Exception {
        mockMvc.perform(get("/api/admin/questions/export")).andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousCannotExportWrongQuestions() throws Exception {
        mockMvc.perform(get("/api/wrong-questions/export")).andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousCannotExportFavorites() throws Exception {
        mockMvc.perform(get("/api/favorites/export")).andExpect(status().isUnauthorized());
    }

    @Test
    void studentCannotExportQuestionBank() throws Exception {
        mockMvc.perform(get("/api/admin/questions/export")
                        .with(user("student").roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotAccessQuestionImportEndpoints() throws Exception {
        MockMultipartFile file = upload();
        mockMvc.perform(get("/api/admin/questions/import/template")).andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/admin/questions/import/validate").file(file)).andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/admin/questions/import/execute").file(file)).andExpect(status().isUnauthorized());
    }

    @Test
    void studentCannotAccessQuestionImportEndpoints() throws Exception {
        MockMultipartFile file = upload();
        mockMvc.perform(get("/api/admin/questions/import/template").with(user("student").roles("STUDENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/admin/questions/import/validate").file(file)
                        .with(user("student").roles("STUDENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/admin/questions/import/execute").file(file)
                        .with(user("student").roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAccessQuestionImportEndpoints() throws Exception {
        MockMultipartFile file = upload();
        QuestionImportValidationResult validation = new QuestionImportValidationResult(
                true, 0, 0, 0, 0, 0, 0, List.of(), List.of(), List.of());
        when(questionImportService.createTemplate()).thenReturn(new ExcelExportFile("template.xlsx", new byte[]{1}));
        when(questionImportService.validate(any())).thenReturn(validation);
        when(questionImportService.execute(any(), eq(1L))).thenReturn(new QuestionImportResult(0, 0));
        UsernamePasswordAuthenticationToken admin = new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        mockMvc.perform(get("/api/admin/questions/import/template").with(authentication(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/admin/questions/import/validate").file(file).with(authentication(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/admin/questions/import/execute").file(file).with(authentication(admin)))
                .andExpect(status().isOk());
    }

    private MockMultipartFile upload() {
        return new MockMultipartFile("file", "questions.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1});
    }
}
