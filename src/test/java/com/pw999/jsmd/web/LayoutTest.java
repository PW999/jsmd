package com.pw999.jsmd.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pw999.jsmd.document.DocumentDownloadService;
import com.pw999.jsmd.document.DocumentRepository;
import com.pw999.jsmd.queue.QueueMonitor;
import com.pw999.jsmd.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(properties = {"jsmd.admin.username=admin", "jsmd.admin.password=s3cret-pw"})
@Import(SecurityConfig.class)
@WithMockUser(username = "admin")
class LayoutTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    DocumentRepository documents;

    @MockitoBean
    DocumentDownloadService downloads;

    @MockitoBean
    QueueMonitor queueMonitor;

    @Test
    void homeRendersBannerMenuAndContentRegions() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<header class=\"banner\">")))
                .andExpect(content().string(containsString("<nav class=\"menu\"")))
                .andExpect(content().string(containsString("<main class=\"content\">")))
                .andExpect(content().string(containsString("Welcome")));
    }

    @Test
    void bannerShowsApplicationNameUsernameAndCsrfProtectedLogout() throws Exception {
        mvc.perform(get("/"))
                .andExpect(content().string(containsString("<span class=\"app-name\">jsmd</span>")))
                .andExpect(content().string(containsString("<span class=\"username\">admin</span>")))
                .andExpect(content().string(containsString("action=\"/logout\"")))
                .andExpect(content().string(containsString("method=\"post\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("Log out")));
    }

    @Test
    void homeMarksHomeEntryActive() throws Exception {
        mvc.perform(get("/"))
                .andExpect(content().string(containsString("<a href=\"/\" aria-current=\"page\" class=\"active\">Home</a>")))
                .andExpect(content().string(not(containsString("aria-current=\"page\" class=\"active\">Documents"))));
    }

    @Test
    void documentsListRendersInLayoutAndMarksEntryActive() throws Exception {
        mvc.perform(get("/documents"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<header class=\"banner\">")))
                .andExpect(content().string(containsString("<nav class=\"menu\"")))
                .andExpect(content().string(containsString("No documents have been indexed yet.")))
                .andExpect(content().string(containsString("aria-current=\"page\" class=\"active\">Documents</a>")))
                .andExpect(content().string(not(containsString("aria-current=\"page\" class=\"active\">Home"))));
    }

    @Test
    void menuLinksToBothSections() throws Exception {
        mvc.perform(get("/"))
                .andExpect(content().string(containsString("href=\"/documents\"")))
                .andExpect(content().string(containsString("href=\"/\"")));
    }

    @Test
    void queuePageRendersInLayoutAndMarksEntryActive() throws Exception {
        mvc.perform(get("/queue"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<header class=\"banner\">")))
                .andExpect(content().string(containsString("href=\"/queue\"")))
                .andExpect(content().string(containsString("aria-current=\"page\" class=\"active\">Queue</a>")))
                .andExpect(content().string(not(containsString("aria-current=\"page\" class=\"active\">Home"))))
                .andExpect(content().string(not(containsString("aria-current=\"page\" class=\"active\">Documents"))));
    }

    @Test
    void menuEntryForQueueIsNotActiveOnOtherPages() throws Exception {
        mvc.perform(get("/"))
                .andExpect(content().string(containsString("href=\"/queue\"")))
                .andExpect(content().string(not(containsString("aria-current=\"page\" class=\"active\">Queue"))));
    }
}
