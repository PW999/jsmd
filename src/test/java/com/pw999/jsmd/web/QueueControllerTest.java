package com.pw999.jsmd.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.pw999.jsmd.document.DocumentDownloadService;
import com.pw999.jsmd.document.DocumentRepository;
import com.pw999.jsmd.queue.QueueMonitor;
import com.pw999.jsmd.queue.QueueMonitor.QueueCounts;
import com.pw999.jsmd.queue.QueueMonitor.QueueItemRow;
import com.pw999.jsmd.queue.QueueName;
import com.pw999.jsmd.security.SecurityConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(properties = {"jsmd.admin.username=admin", "jsmd.admin.password=s3cret-pw"})
@Import(SecurityConfig.class)
@WithMockUser(username = "admin")
class QueueControllerTest {

    private static final Instant CREATED = Instant.parse("2026-10-21T09:51:18Z");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    QueueMonitor monitor;

    @MockitoBean
    DocumentRepository documents;

    @MockitoBean
    DocumentDownloadService downloads;

    private static List<QueueCounts> counts(long processFailed) {
        return List.of(new QueueCounts(QueueName.PROCESS, 2, 1, processFailed, 3, 0),
                new QueueCounts(QueueName.DELETE, 0, 0, 0, 0, 0));
    }

    private static QueueItemRow item(long id, String status, String error, String path) {
        return new QueueItemRow(id, QueueName.PROCESS, status, 2, error, CREATED, path);
    }

    @Test
    void showsCountsAndItemsInTheQueueView() throws Exception {
        given(monitor.counts()).willReturn(List.of(new QueueCounts(QueueName.PROCESS, 1, 0, 1, 0, 0),
                new QueueCounts(QueueName.DELETE, 0, 0, 0, 0, 0)));
        given(monitor.items(200)).willReturn(List.of(
                item(7, "FAILED", "boom", "/data/a.pdf"), item(8, "PENDING", null, null)));

        mvc.perform(get("/queue"))
                .andExpect(status().isOk())
                .andExpect(view().name("queue"))
                .andExpect(model().attribute("active", "queue"))
                .andExpect(model().attribute("failedCount", 1L))
                .andExpect(model().attribute("truncated", false))
                .andExpect(content().string(containsString("<header class=\"banner\">")))
                .andExpect(content().string(containsString("PROCESS")))
                .andExpect(content().string(containsString("DELETE")))
                .andExpect(content().string(containsString("boom")))
                .andExpect(content().string(containsString("/data/a.pdf")))
                .andExpect(content().string(containsString("2026-10-21 09:51:18Z")))
                .andExpect(content().string(not(containsString("The queues are empty."))))
                .andExpect(content().string(not(containsString("Showing the first"))));
    }

    @Test
    void refreshesLiveRegionEveryFiveSecondsWithoutReloadingThePage() throws Exception {
        given(monitor.counts()).willReturn(counts(0));
        given(monitor.items(200)).willReturn(List.of());

        mvc.perform(get("/queue"))
                .andExpect(content().string(containsString("data-refresh-seconds=\"5\"")))
                .andExpect(content().string(containsString("id=\"queue-live\"")))
                .andExpect(content().string(containsString("fetch(location.pathname")))
                .andExpect(content().string(not(containsString("http-equiv=\"refresh\""))));
    }

    @Test
    void emptyQueuesAreStated() throws Exception {
        given(monitor.counts()).willReturn(List.of(new QueueCounts(QueueName.PROCESS, 0, 0, 0, 0, 0),
                new QueueCounts(QueueName.DELETE, 0, 0, 0, 0, 0)));
        given(monitor.items(200)).willReturn(List.of());

        mvc.perform(get("/queue"))
                .andExpect(content().string(containsString("The queues are empty.")))
                .andExpect(content().string(not(containsString("queue-items"))))
                .andExpect(content().string(containsString("action=\"/queue/purge\"")))
                .andExpect(content().string(containsString("Purge finished items")));
    }

    @Test
    void truncatedListIsAnnouncedWithFullCounts() throws Exception {
        given(monitor.counts()).willReturn(List.of(new QueueCounts(QueueName.PROCESS, 0, 0, 0, 500, 0),
                new QueueCounts(QueueName.DELETE, 0, 0, 0, 0, 0)));
        given(monitor.items(200)).willReturn(List.of(item(1, "DONE", null, null)));

        mvc.perform(get("/queue"))
                .andExpect(model().attribute("truncated", true))
                .andExpect(content().string(containsString("Showing the first 200 of 500 items.")));
    }

    @Test
    void removeControlOnlyAppearsForFailedItemsAndPurgeAlwaysDoes() throws Exception {
        given(monitor.counts()).willReturn(counts(1));
        given(monitor.items(200)).willReturn(List.of(
                item(7, "FAILED", "boom", null), item(8, "PENDING", null, null), item(9, "CLAIMED", null, null)));

        mvc.perform(get("/queue"))
                .andExpect(content().string(containsString("action=\"/queue/items/7/remove\"")))
                .andExpect(content().string(not(containsString("/queue/items/8/remove"))))
                .andExpect(content().string(not(containsString("/queue/items/9/remove"))))
                .andExpect(content().string(containsString("action=\"/queue/purge\"")))
                .andExpect(content().string(containsString("Remove all finished items (failed, done, cancelled)")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));

        given(monitor.counts()).willReturn(counts(0));
        given(monitor.items(200)).willReturn(List.of(item(8, "PENDING", null, null)));

        mvc.perform(get("/queue"))
                .andExpect(content().string(not(containsString("/remove"))))
                .andExpect(content().string(containsString("action=\"/queue/purge\"")));
    }

    @Test
    void textWithMarkupIsEscaped() throws Exception {
        given(monitor.counts()).willReturn(counts(1));
        given(monitor.items(200)).willReturn(List.of(
                item(7, "FAILED", "<script>alert(1)</script>", "/data/<b>x</b>.pdf")));

        mvc.perform(get("/queue"))
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(containsString("/data/&lt;b&gt;x&lt;/b&gt;.pdf")))
                .andExpect(content().string(not(containsString("<script>alert"))));
    }

    @Test
    void removingAFailedItemRedirectsWithMessage() throws Exception {
        given(monitor.removeFailed(7)).willReturn(true);

        mvc.perform(post("/queue/items/7/remove").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/queue"))
                .andExpect(flash().attribute("message", "Item 7 removed."));
        verify(monitor).removeFailed(7);
    }

    @Test
    void removingAnItemThatIsNotFailedSaysSo() throws Exception {
        given(monitor.removeFailed(8)).willReturn(false);
        given(monitor.exists(8)).willReturn(true);

        mvc.perform(post("/queue/items/8/remove").with(csrf()))
                .andExpect(redirectedUrl("/queue"))
                .andExpect(flash().attribute("error", "Item 8 could not be removed: it is not failed."));
    }

    @Test
    void removingAMissingItemSaysNotFound() throws Exception {
        given(monitor.removeFailed(9)).willReturn(false);
        given(monitor.exists(9)).willReturn(false);

        mvc.perform(post("/queue/items/9/remove").with(csrf()))
                .andExpect(redirectedUrl("/queue"))
                .andExpect(flash().attribute("error", "Item 9 was not found."));
    }

    @Test
    void purgeReportsHowManyWereRemoved() throws Exception {
        given(monitor.purgeFinished()).willReturn(3);

        mvc.perform(post("/queue/purge").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/queue"))
                .andExpect(flash().attribute("message", "Purged 3 finished item(s)."));
        verify(monitor).purgeFinished();
    }

    @Test
    void flashMessageIsShownOnThePage() throws Exception {
        given(monitor.counts()).willReturn(counts(0));
        given(monitor.items(200)).willReturn(List.of());

        mvc.perform(get("/queue").flashAttr("message", "Purged 3 finished item(s)."))
                .andExpect(content().string(containsString("Purged 3 finished item(s).")));
        mvc.perform(get("/queue").flashAttr("error", "Item 9 was not found."))
                .andExpect(content().string(containsString("Item 9 was not found.")));
    }

    @Test
    @WithAnonymousUser
    void anonymousRequestsRedirectToLoginAndChangeNothing() throws Exception {
        mvc.perform(get("/queue")).andExpect(redirectedUrl("/login"));
        mvc.perform(post("/queue/items/7/remove").with(csrf())).andExpect(redirectedUrl("/login"));
        mvc.perform(post("/queue/purge").with(csrf())).andExpect(redirectedUrl("/login"));

        verifyNoInteractions(monitor);
    }

    @Test
    void postsWithoutCsrfTokenAreRejectedAndChangeNothing() throws Exception {
        mvc.perform(post("/queue/items/7/remove")).andExpect(status().isForbidden());
        mvc.perform(post("/queue/purge")).andExpect(status().isForbidden());

        verify(monitor, never()).removeFailed(7);
        verify(monitor, never()).purgeFinished();
    }
}
