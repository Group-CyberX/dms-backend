package com.dms.service;

import com.dms.models.Comment;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Writes review comments into a PDF so the discussion survives outside the DMS.
 *
 * Each anchored comment produces two things on the page: a real PDF text
 * annotation (the sticky-note icon a PDF reader can open) and a small numbered
 * marker drawn on the page, so the position is visible even in a viewer that
 * ignores annotations. A summary page listing every comment is appended at the
 * end, which is what makes the exported file useful on its own.
 *
 * Coordinate handling matches PdfSignatureStampingService: the browser reports
 * fractions of the page from the top-left, PDF user space runs from the
 * bottom-left, so Y is flipped here.
 */
@Service
public class PdfAnnotationStampingService {

    private static final float MARKER_RADIUS = 9f;

    // Typewriter text: dark blue, so it reads as something that was filled in
    // rather than something that was printed.
    private static final float TYPEWRITER_FONT_SIZE = 11f;
    private static final float TYPEWRITER_MARGIN = 36f;
    private static final float TYPEWRITER_MIN_WIDTH = 120f;
    private static final Color TYPEWRITER_COLOR = new Color(0x10, 0x3A, 0x7A);

    private static final DateTimeFormatter STAMP_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    /**
     * @param comments  the comments to write in, in display order
     * @param usernames comment author id -> display name, for the labels
     * @return the annotated PDF; the input array is not modified
     */
    public byte[] stamp(byte[] pdfBytes, List<Comment> comments, Map<String, String> usernames)
            throws IOException {

        if (comments == null || comments.isEmpty()) {
            throw new IllegalArgumentException("There are no comments to write into the document");
        }

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            int pageCount = document.getNumberOfPages();
            List<Comment> ordered = new ArrayList<>(comments);

            // Typewriter text is not part of the numbered discussion - it
            // becomes part of the document itself - so only real comments are
            // numbered, and only they reach the summary page.
            List<Comment> discussion = ordered.stream()
                    .filter(c -> !c.isTypewriter())
                    .toList();

            int number = 0;
            for (Comment comment : ordered) {
                boolean typewriter = comment.isTypewriter();
                if (!typewriter) {
                    number++;
                }

                if (!comment.isAnchored()) {
                    continue; // general comments appear only on the summary page
                }
                int pageIndex = comment.getPageNumber() - 1;
                if (pageIndex < 0 || pageIndex >= pageCount) {
                    continue; // stale anchor - skip rather than fail the whole save
                }

                PDPage page = document.getPage(pageIndex);
                if (typewriter) {
                    drawTypewriterText(document, page, comment);
                } else {
                    drawMarker(document, page, comment, number,
                            usernames.getOrDefault(String.valueOf(comment.getUserId()), "Reviewer"));
                }
            }

            if (!discussion.isEmpty()) {
                appendSummaryPage(document, discussion, usernames);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    /**
     * Typewriter: draws the text straight onto the page at the anchor, the way
     * someone would type into a blank on a printed form.
     *
     * No marker and no number - once saved this is part of the document, not a
     * remark about it. Long text wraps rather than running off the page edge,
     * and the block grows downward from the click point.
     */
    private void drawTypewriterText(PDDocument document, PDPage page, Comment comment) throws IOException {
        PDRectangle box = page.getMediaBox();
        float pw = box.getWidth();
        float ph = box.getHeight();

        float x = (float) (comment.getAnchorX() * pw) + box.getLowerLeftX();
        // Flip: the browser measures down from the top, PDF measures up.
        float y = box.getUpperRightY() - (float) (comment.getAnchorY() * ph);

        // Wrap to whatever room is left between the anchor and the right margin.
        float available = Math.max(pw - x - TYPEWRITER_MARGIN, TYPEWRITER_MIN_WIDTH);
        int maxChars = Math.max((int) (available / (TYPEWRITER_FONT_SIZE * 0.5f)), 12);
        List<String> lines = wrap(sanitise(comment.getContent()), maxChars);

        try (PDPageContentStream content = new PDPageContentStream(
                document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {

            content.beginText();
            content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), TYPEWRITER_FONT_SIZE);
            content.setNonStrokingColor(TYPEWRITER_COLOR);
            content.setLeading(TYPEWRITER_FONT_SIZE * 1.25f);
            // Sit the first line just below the click point, so the text starts
            // where the cursor was rather than above it.
            content.newLineAtOffset(x, y - TYPEWRITER_FONT_SIZE);

            for (String line : lines) {
                content.showText(line);
                content.newLine();
            }
            content.endText();
        }
    }

    /** Numbered dot on the page plus a real PDF annotation holding the text. */
    private void drawMarker(PDDocument document, PDPage page, Comment comment, int number, String author)
            throws IOException {

        PDRectangle box = page.getMediaBox();
        float pw = box.getWidth();
        float ph = box.getHeight();

        float x = (float) (comment.getAnchorX() * pw) + box.getLowerLeftX();
        // Flip: the browser measures down from the top, PDF measures up from the bottom.
        float y = box.getUpperRightY() - (float) (comment.getAnchorY() * ph);

        try (PDPageContentStream content = new PDPageContentStream(
                document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {

            // Slight transparency so the marker never hides the text underneath.
            PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
            gs.setNonStrokingAlphaConstant(0.85f);
            content.setGraphicsStateParameters(gs);

            content.setNonStrokingColor(new Color(0x8B, 0x2E, 0x00));
            drawCircle(content, x, y, MARKER_RADIUS);
            content.fill();

            content.beginText();
            content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 9f);
            content.setNonStrokingColor(Color.WHITE);
            // Nudge so the digits sit centred in the dot.
            content.newLineAtOffset(x - (number > 9 ? 5.5f : 2.5f), y - 3f);
            content.showText(String.valueOf(number));
            content.endText();
        }

        // The real annotation: openable in any PDF reader.
        PDAnnotationText note = new PDAnnotationText();
        note.setName(PDAnnotationText.NAME_COMMENT);
        note.setContents(comment.getContent());
        note.setTitlePopup(author);
        note.setSubject("Comment " + number);
        note.setRectangle(new PDRectangle(x - MARKER_RADIUS, y - MARKER_RADIUS,
                MARKER_RADIUS * 2, MARKER_RADIUS * 2));
        note.setColor(new org.apache.pdfbox.pdmodel.graphics.color.PDColor(
                new float[]{0.545f, 0.180f, 0.0f},
                org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB.INSTANCE));
        page.getAnnotations().add(note);
    }

    /** A closing page listing every comment, numbered to match the markers. */
    private void appendSummaryPage(PDDocument document, List<Comment> comments, Map<String, String> usernames)
            throws IOException {

        PDPage summary = new PDPage(PDRectangle.A4);
        document.addPage(summary);

        float margin = 56f;
        float y = summary.getMediaBox().getUpperRightY() - margin;
        float width = summary.getMediaBox().getWidth() - (margin * 2);

        try (PDPageContentStream content = new PDPageContentStream(document, summary)) {

            content.beginText();
            content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 16f);
            content.setNonStrokingColor(new Color(0x1a, 0x1a, 0x1a));
            content.newLineAtOffset(margin, y);
            content.showText("Review Comments");
            content.endText();

            y -= 26f;
            content.setStrokingColor(new Color(0xd0, 0xd0, 0xd0));
            content.moveTo(margin, y);
            content.lineTo(margin + width, y);
            content.stroke();
            y -= 24f;

            int number = 0;
            for (Comment comment : comments) {
                number++;
                if (y < margin + 60f) {
                    break; // one summary page is enough; the rest stay in the DMS
                }

                String author = usernames.getOrDefault(String.valueOf(comment.getUserId()), "Reviewer");
                String where = comment.isAnchored() ? "page " + comment.getPageNumber() : "general";
                String when = comment.getCreatedAt() != null
                        ? comment.getCreatedAt().format(STAMP_FORMAT) : "";

                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 10f);
                content.setNonStrokingColor(new Color(0x8B, 0x2E, 0x00));
                content.newLineAtOffset(margin, y);
                content.showText(number + ".  " + sanitise(author) + "  -  " + where + "  -  " + when);
                content.endText();
                y -= 14f;

                for (String line : wrap(sanitise(comment.getContent()), 95)) {
                    if (y < margin) break;
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10f);
                    content.setNonStrokingColor(new Color(0x33, 0x33, 0x33));
                    content.newLineAtOffset(margin + 14f, y);
                    content.showText(line);
                    content.endText();
                    y -= 13f;
                }
                y -= 10f;
            }
        }
    }

    private void drawCircle(PDPageContentStream content, float cx, float cy, float r) throws IOException {
        // Four Bezier curves approximate a circle closely enough at this size.
        final float k = 0.5523f * r;
        content.moveTo(cx - r, cy);
        content.curveTo(cx - r, cy + k, cx - k, cy + r, cx, cy + r);
        content.curveTo(cx + k, cy + r, cx + r, cy + k, cx + r, cy);
        content.curveTo(cx + r, cy - k, cx + k, cy - r, cx, cy - r);
        content.curveTo(cx - k, cy - r, cx - r, cy - k, cx - r, cy);
        content.closePath();
    }

    /** Standard 14 fonts are WinAnsi only, so drop anything they cannot render. */
    private String sanitise(String text) {
        if (text == null) return "";
        return text.replaceAll("[^\\x20-\\x7E]", " ").trim();
    }

    private List<String> wrap(String text, int maxChars) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) return lines;

        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (line.length() + word.length() + 1 > maxChars) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                if (line.length() > 0) line.append(' ');
                line.append(word);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }
}
