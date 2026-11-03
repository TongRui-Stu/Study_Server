package com.tongrui.study.security;

import com.tongrui.study.config.FrontendController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.web.enabled=true")
@AutoConfigureMockMvc
class PackagedFrontendTests {
    @Autowired private MockMvc mvc;

    @Test
    void browserHistoryRoutesOpenWithoutApiAuthentication() throws Exception {
        for (String route : FrontendController.PAGE_ROUTES) {
            mvc.perform(get(route.replace("{id}", "1")))
                    .andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        }
    }

    @Test
    void frontendHostingDoesNotExposeProtectedApis() throws Exception {
        for (String route : new String[]{"/api/auth/me", "/api/subjects", "/api/admin/questions", "/api/not-found"}) {
            mvc.perform(get(route)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void unavailableAssetsAreNotReplacedWithAnHtmlPage() throws Exception {
        mvc.perform(get("/assets/missing-module.js")).andExpect(status().isNotFound());
    }
}
