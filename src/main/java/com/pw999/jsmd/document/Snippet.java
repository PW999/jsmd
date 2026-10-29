package com.pw999.jsmd.document;

import java.util.ArrayList;
import java.util.List;

/**
 * A fragment of document text with the matched words marked. The text is split into plain and
 * highlighted segments so a template can render every segment as escaped text.
 *
 * <p>The markers are private-use characters that {@code ts_headline} is told to put around matches;
 * any marker already present in the document text is dropped before it can be mistaken for one.
 */
public record Snippet(List<Segment> segments) {

    public static final char START = '';
    public static final char STOP = '';

    /**
     * {@code ts_headline} drops anything that looks like an HTML tag and decodes entities, so the characters
     * that start them are swapped for private-use ones before the call and restored by {@link #parse}.
     */
    public static final String MASKED = "<>&";
    public static final String MASKS = "";

    public record Segment(String text, boolean highlighted) {
    }

    public Snippet {
        segments = List.copyOf(segments);
    }

    /** Splits headline output on the markers; null or empty input gives no segments. */
    public static Snippet parse(String headline) {
        List<Segment> segments = new ArrayList<>();
        if (headline == null) {
            return new Snippet(segments);
        }
        StringBuilder current = new StringBuilder();
        boolean highlighted = false;
        for (int i = 0; i < headline.length(); i++) {
            char c = headline.charAt(i);
            if (c == START && !highlighted) {
                flush(segments, current, false);
                highlighted = true;
            } else if (c == STOP && highlighted) {
                flush(segments, current, true);
                highlighted = false;
            } else if (c != START && c != STOP) {
                int mask = MASKS.indexOf(c);
                current.append(mask >= 0 ? MASKED.charAt(mask) : c);
            }
        }
        flush(segments, current, highlighted);
        return new Snippet(segments);
    }

    private static void flush(List<Segment> segments, StringBuilder current, boolean highlighted) {
        if (!current.isEmpty()) {
            segments.add(new Segment(current.toString(), highlighted));
            current.setLength(0);
        }
    }
}
