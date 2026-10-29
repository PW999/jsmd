package com.pw999.jsmd.queue;

public enum QueueName {
    /** New content that still has to be analyzed. */
    PROCESS,
    /** Content that no longer has any file on disk. The queue only records the intent to delete. */
    DELETE
}
