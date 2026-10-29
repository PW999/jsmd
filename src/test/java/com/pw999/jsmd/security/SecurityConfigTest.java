package com.pw999.jsmd.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pw999.jsmd.document.DocumentDownloadService;
import com.pw999.jsmd.document.DocumentRepository;
import com.pw999.jsmd.queue.QueueMonitor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(properties = {"jsmd.admin.username=admin", "jsmd.admin.password=s3cret-pw"})
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    DocumentRepository documents;

    @MockitoBean
    DocumentDownloadService downloads;

    @MockitoBean
    QueueMonitor queueMonitor;

    @Test
    void anonymousRequestRedirectsToLogin() throws Exception {
        mvc.perform(get("/documents"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void anonymousSearchRedirectsToLoginWithoutQueryingTheDatabase() throws Exception {
        mvc.perform(get("/documents").param("q", "anything"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        org.mockito.Mockito.verifyNoInteractions(documents);
    }

    @Test
    void loginPageIsOpen() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Sign in")));
    }

    @Test
    void returnsToRequestedPageAfterLogin() throws Exception {
        MvcResult first = mvc.perform(get("/documents")).andReturn();
        MockHttpSession session = (MockHttpSession) first.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mvc.perform(post("/login").session(session).with(csrf())
                        .param("username", "admin").param("password", "s3cret-pw"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/documents?continue"));
    }

    @Test
    void successfulLoginStartsSessionAndGoesToRoot() throws Exception {
        mvc.perform(post("/login").with(csrf())
                        .param("username", "admin").param("password", "s3cret-pw"))
                .andExpect(authenticated().withUsername("admin"))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void failedLoginShowsGenericErrorAndStartsNoSession() throws Exception {
        mvc.perform(post("/login").with(csrf())
                        .param("username", "admin").param("password", "wrong"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
        mvc.perform(post("/login").with(csrf())
                        .param("username", "nobody").param("password", "s3cret-pw"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));

        mvc.perform(get("/login").param("error", ""))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Invalid username or password.")));
    }

    @Test
    void loginRequiresCsrfToken() throws Exception {
        mvc.perform(post("/login").param("username", "admin").param("password", "s3cret-pw"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin")
    void logoutEndsSessionAndShowsSignedOutMessage() throws Exception {
        mvc.perform(post("/logout").with(csrf()))
                .andExpect(redirectedUrl("/login?logout"))
                .andExpect(unauthenticated());

        mvc.perform(get("/login").param("logout", ""))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("You have been signed out.")));
    }

    @Test
    void protectedPageRedirectsAfterLogout() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/login").session(session).with(csrf())
                .param("username", "admin").param("password", "s3cret-pw"));
        mvc.perform(post("/logout").session(session).with(csrf()));

        mvc.perform(get("/documents").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void logoutViaGetIsNotAccepted() throws Exception {
        mvc.perform(get("/logout"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
