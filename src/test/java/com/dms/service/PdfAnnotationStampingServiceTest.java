package com.dms.service;

import com.dms.models.Comment;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the two ways an annotation reaches the saved PDF.
 *
 * A comment becomes a numbered mark plus an entry on a summary page. Typewriter
 * text becomes words on the page itself and appears nowhere else - it is part of
 * the document once saved, not a remark about it.
 */
class PdfAnnotationStampingServiceTest {

    private PdfAnnotationStampingService service;

    @BeforeEach
    void setUp() {
        service = new PdfAnnotationStampingService();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** A blank one-page A4 document to annotate. */
    private byte[] blankPdf() throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(PDRectangle.A4));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private Comment comment(String content, String type, Integer page, Double x, Double y) {
        Comment c = new Comment();
        c.setId(UUID.randomUUID());
        c.setUserId(UUID.randomUUID());
        c.setContent(content);
        c.setAnnotationType(type);
        c.setPageNumber(page);
        c.setAnchorX(x);
        c.setAnchorY(y);
        return c;
    }

    private String textOfPage(byte[] pdf, int pageNumber) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(pageNumber);
            stripper.setEndPage(pageNumber);
            return stripper.getText(document);
        }
    }

    private int pageCount(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return document.getNumberOfPages();
        }
    }

    // ------------------------------------------------------------------
    // typewriter text
    // ------------------------------------------------------------------

    @Test
    @DisplayName("typewriter text is written onto the page it was placed on")
    void typewriterTextLandsOnThePage() throws IOException {
        byte[] result = service.stamp(
                blankPdf(),
                List.of(comment("Approved by Finance", Comment.TYPE_TEXT, 1, 0.2, 0.3)),
                Map.of());

        assertTrue(textOfPage(result, 1).contains("Approved by Finance"),
                "the typed text should appear on the page itself");
    }

    @Test
    @DisplayName("typewriter text alone adds no summary page")
    void typewriterTextDoesNotCreateASummaryPage() throws IOException {
        byte[] original = blankPdf();
        byte[] result = service.stamp(
                original,
                List.of(comment("Filled in", Comment.TYPE_TEXT, 1, 0.5, 0.5)),
                Map.of());

        assertEquals(pageCount(original), pageCount(result),
                "typed text becomes part of the document, so it needs no summary");
    }

    @Test
    @DisplayName("long typewriter text wraps instead of running off the page")
    void longTypewriterTextWraps() throws IOException {
        String sentence = "This clause has been reviewed and accepted in full by the "
                + "finance department following the quarterly audit of purchase orders.";

        byte[] result = service.stamp(
                blankPdf(),
                List.of(comment(sentence, Comment.TYPE_TEXT, 1, 0.1, 0.2)),
                Map.of());

        String extracted = textOfPage(result, 1);
        // Wrapping splits the line, so check the ends rather than the whole
        // string: both must survive for nothing to have been clipped.
        assertTrue(extracted.contains("This clause has been"), "the start of the text is missing");
        assertTrue(extracted.contains("purchase orders."), "the end of the text was clipped");
    }

    @Test
    @DisplayName("unanchored typewriter text is skipped rather than failing the save")
    void unanchoredTypewriterTextIsSkipped() {
        Comment floating = comment("nowhere to put this", Comment.TYPE_TEXT, null, null, null);

        // It is not drawable, and it is not discussion either, so there is
        // nothing to write - that must not throw.
        assertDoesNotThrow(() -> service.stamp(blankPdf(), List.of(floating), Map.of()));
    }

    // ------------------------------------------------------------------
    // ordinary comments
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a comment still gets a summary page listing it")
    void commentsGetASummaryPage() throws IOException {
        byte[] original = blankPdf();
        byte[] result = service.stamp(
                original,
                List.of(comment("Please check the totals", Comment.TYPE_COMMENT, 1, 0.4, 0.4)),
                Map.of());

        assertEquals(pageCount(original) + 1, pageCount(result),
                "a comment should add exactly one summary page");
        assertTrue(textOfPage(result, pageCount(result)).contains("Please check the totals"),
                "the comment text belongs on the summary page");
    }

    @Test
    @DisplayName("comment numbering ignores typewriter entries")
    void numberingSkipsTypewriterEntries() throws IOException {
        // Typed text sits between two comments; the comments must still be
        // numbered 1 and 2, because the summary page only lists them.
        byte[] result = service.stamp(
                blankPdf(),
                List.of(
                        comment("First remark", Comment.TYPE_COMMENT, 1, 0.2, 0.2),
                        comment("typed onto the form", Comment.TYPE_TEXT, 1, 0.3, 0.4),
                        comment("Second remark", Comment.TYPE_COMMENT, 1, 0.5, 0.6)),
                Map.of());

        String summary = textOfPage(result, pageCount(result));
        assertTrue(summary.contains("First remark"), "first comment missing from the summary");
        assertTrue(summary.contains("Second remark"), "second comment missing from the summary");
        assertFalse(summary.contains("typed onto the form"),
                "typed text is part of the page, so it must not be listed as a remark");
    }

    @Test
    @DisplayName("a null annotation type is treated as an ordinary comment")
    void nullTypeBehavesAsAComment() throws IOException {
        // Rows written before the column existed hold null.
        byte[] original = blankPdf();
        byte[] result = service.stamp(
                original,
                List.of(comment("Legacy comment", null, 1, 0.5, 0.5)),
                Map.of());

        assertEquals(pageCount(original) + 1, pageCount(result));
        assertTrue(textOfPage(result, pageCount(result)).contains("Legacy comment"));
    }

    @Test
    @DisplayName("an out-of-range page anchor is skipped, not fatal")
    void staleAnchorIsSkipped() {
        Comment stale = comment("points at page 9", Comment.TYPE_TEXT, 9, 0.5, 0.5);
        assertDoesNotThrow(() -> service.stamp(blankPdf(), List.of(stale), Map.of()));
    }

    @Test
    @DisplayName("stamping nothing is refused")
    void emptyAnnotationListIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> service.stamp(blankPdf(), List.of(), Map.of()));
    }
}
