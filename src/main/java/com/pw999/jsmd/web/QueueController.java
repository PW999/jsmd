package com.pw999.jsmd.web;

import com.pw999.jsmd.queue.QueueMonitor;
import com.pw999.jsmd.queue.QueueMonitor.QueueCounts;
import com.pw999.jsmd.queue.QueueMonitor.QueueItemRow;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Shows the work queues, removes failed items and purges finished ones. */
@Controller
class QueueController {

    static final int ITEM_LIMIT = 200;
    static final int REFRESH_SECONDS = 5;

    private final QueueMonitor monitor;

    QueueController(QueueMonitor monitor) {
        this.monitor = monitor;
    }

    @GetMapping("/queue")
    String show(Model model) {
        List<QueueCounts> counts = monitor.counts();
        List<QueueItemRow> items = monitor.items(ITEM_LIMIT);
        long total = counts.stream().mapToLong(QueueCounts::total).sum();
        long failed = counts.stream().mapToLong(QueueCounts::failed).sum();

        model.addAttribute("active", "queue");
        model.addAttribute("counts", counts);
        model.addAttribute("items", items);
        model.addAttribute("total", total);
        model.addAttribute("failedCount", failed);
        model.addAttribute("truncated", total > items.size());
        model.addAttribute("limit", ITEM_LIMIT);
        model.addAttribute("refreshSeconds", REFRESH_SECONDS);
        return "queue";
    }

    @PostMapping("/queue/items/{id}/remove")
    String remove(@PathVariable long id, RedirectAttributes redirect) {
        if (monitor.removeFailed(id)) {
            redirect.addFlashAttribute("message", "Item " + id + " removed.");
        } else if (monitor.exists(id)) {
            redirect.addFlashAttribute("error", "Item " + id + " could not be removed: it is not failed.");
        } else {
            redirect.addFlashAttribute("error", "Item " + id + " was not found.");
        }
        return "redirect:/queue";
    }

    @PostMapping("/queue/purge")
    String purge(RedirectAttributes redirect) {
        int removed = monitor.purgeFinished();
        redirect.addFlashAttribute("message", "Purged " + removed + " finished item(s).");
        return "redirect:/queue";
    }
}
