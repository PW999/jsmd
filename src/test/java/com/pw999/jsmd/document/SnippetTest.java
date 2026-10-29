package com.pw999.jsmd.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.pw999.jsmd.document.Snippet.Segment;
import org.junit.jupiter.api.Test;

class SnippetTest {

    private static final String S = String.valueOf(Snippet.START);
    private static final String E = String.valueOf(Snippet.STOP);

    @Test
    void textWithoutMarkersIsOnePlainSegment() {
        assertThat(Snippet.parse("just text").segments()).containsExactly(new Segment("just text", false));
    }

    @Test
    void severalMatchesSplitIntoAlternatingSegments() {
        Snippet snippet = Snippet.parse("an " + S + "invoice" + E + " and another " + S + "Invoice" + E);

        assertThat(snippet.segments()).containsExactly(
                new Segment("an ", false),
                new Segment("invoice", true),
                new Segment(" and another ", false),
                new Segment("Invoice", true));
    }

    @Test
    void markupAndMarkerLikeTextStaysLiteral() {
        Snippet snippet = Snippet.parse("<script>a & b << c >> " + S + "d" + E);

        assertThat(snippet.segments()).containsExactly(
                new Segment("<script>a & b << c >> ", false),
                new Segment("d", true));
    }

    @Test
    void markerCharactersFromTheDocumentCannotCreateHighlights() {
        assertThat(Snippet.parse("a" + E + "b").segments()).containsExactly(new Segment("ab", false));
        assertThat(Snippet.parse("a" + S + S + "b" + E).segments())
                .containsExactly(new Segment("a", false), new Segment("b", true));
    }

    @Test
    void maskedMarkupCharactersAreRestored() {
        assertThat(Snippet.parse("b amp; " + S + "x" + E).segments()).containsExactly(
                new Segment("<b> &amp; ", false),
                new Segment("x", true));
    }

    @Test
    void unterminatedHighlightIsKeptAsHighlight() {
        assertThat(Snippet.parse(S + "tail").segments()).containsExactly(new Segment("tail", true));
    }

    @Test
    void nullAndEmptyGiveNoSegments() {
        assertThat(Snippet.parse(null).segments()).isEmpty();
        assertThat(Snippet.parse("").segments()).isEmpty();
    }
}
