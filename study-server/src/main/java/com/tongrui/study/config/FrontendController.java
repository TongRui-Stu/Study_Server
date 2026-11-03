package com.tongrui.study.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Browser history routes for the optional packaged frontend. API paths never enter this controller. */
@Controller
@ConditionalOnProperty(name = "app.web.enabled", havingValue = "true")
public class FrontendController {

    public static final String[] PAGE_ROUTES = {
            "/", "/home", "/login", "/register", "/subjects/{id}", "/chapters/{id}",
            "/practice", "/practice/{id}", "/wrong-questions", "/favorites", "/statistics",
            "/admin", "/admin/subjects", "/admin/chapters", "/admin/knowledge-points",
            "/admin/questions", "/admin/question-import"
    };

    @GetMapping({"/", "/home", "/login", "/register", "/subjects/{id}", "/chapters/{id}",
            "/practice", "/practice/{id}", "/wrong-questions", "/favorites", "/statistics",
            "/admin", "/admin/subjects", "/admin/chapters", "/admin/knowledge-points",
            "/admin/questions", "/admin/question-import"})
    public String index() {
        return "forward:/index.html";
    }
}
