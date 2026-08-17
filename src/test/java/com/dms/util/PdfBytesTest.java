package com.dms.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Deciding whether a stored file is really a PDF.
 *
 * The answer has to come from the bytes. Titles and content types are labels
 * anyone can set: a real PDF stored as "testingsig" was being refused for
 * signing, while a .docx renamed to .pdf would have been accepted and then
 * failed inside PDFBox.
 */
class PdfBytesTest {

    private byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    @Test
    @DisplayName("a real PDF is accepted, whatever it is named")
    void acceptsPdfHeader() {
        assertTrue(PdfBytes.looksLikePdf(bytes("%PDF-1.7\n%âãÏÓ\n1 0 obj")));
    }

    @Test
    @DisplayName("another format is rejected even when it claims to be a PDF")
    void rejectsNonPdf() {
        assertFalse(PdfBytes.looksLikePdf(bytes("PK")));       // docx/xlsx
        assertFalse(PdfBytes.looksLikePdf(bytes("PNG\r\n")));        // png
        assertFalse(PdfBytes.looksLikePdf(bytes("Just some text.")));
    }

    @Test
    @DisplayName("a near miss is not enough - the marker must be exact")
    void rejectsPartialMarker() {
        assertFalse(PdfBytes.looksLikePdf(bytes("%PDF")));   // no trailing dash
        assertFalse(PdfBytes.looksLikePdf(bytes("%pdf-")));  // the marker is uppercase
        assertFalse(PdfBytes.looksLikePdf(bytes(" %PDF-"))); // must start the file
    }

    @Test
    @DisplayName("nothing to read is not a PDF")
    void rejectsEmptyAndNull() {
        assertFalse(PdfBytes.looksLikePdf(null));
        assertFalse(PdfBytes.looksLikePdf(new byte[0]));
        assertFalse(PdfBytes.looksLikePdf(bytes("%PD")));
    }
}
