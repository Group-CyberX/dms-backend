package com.dms.util;

/**
 * Tells whether stored bytes are actually a PDF.
 *
 * Both stamping paths - signing and review annotation - drive PDFBox, which
 * only reads PDFs. A document's title and its stored content type are both
 * just labels: a file can be called ".pdf" and hold something else entirely,
 * and the download endpoint labels every file application/octet-stream
 * regardless. Only the bytes themselves settle it.
 *
 * Checked before stamping so the caller gets a clear answer, rather than
 * PDFBox failing deep in the parser with "Missing root object specification
 * in trailer" - a 500 that tells the signer nothing about what went wrong.
 */
public final class PdfBytes {

    private PdfBytes() {}

    /** Every PDF begins with the five bytes %PDF-, whatever it is named. */
    public static boolean looksLikePdf(byte[] bytes) {
        if (bytes == null || bytes.length < 5) {
            return false;
        }
        return bytes[0] == '%'
            && bytes[1] == 'P'
            && bytes[2] == 'D'
            && bytes[3] == 'F'
            && bytes[4] == '-';
    }
}
