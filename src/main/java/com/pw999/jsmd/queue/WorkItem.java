package com.pw999.jsmd.queue;

/**
 * A claimed queue item.
 *
 * @param attempts number of times the item has been claimed, including this claim
 */
public record WorkItem(long id, QueueName queue, long contentId, int attempts) {
}
