package com.dms.util;

/**
 * Builds storage filenames for copies the server produces itself.
 *
 * Signing and review both save their result as a new version, and both name it
 * after the document's title. The version store accepts only letters, digits,
 * dash and underscore in a filename - a deliberate guard on the path the file
 * is written to - while a title is free text a user typed. Something has to
 * bridge the two, and doing it in one place keeps the two paths from drifting.
 *
 * Both call sites previously used the title as-is, so an ordinary title like
 * "Testing document.pdf" failed at the very end of the work - after the PDF had
 * been stamped - with a message about file names that meant nothing to whoever
 * was signing or reviewing.
 */
public final class StoredFilename {

    private StoredFilename() {}

    /** Longest base name we build, before the suffix and extension. */
    private static final int MAX_BASE_LENGTH = 100;

    /** Used when a title is missing, or holds nothing we can keep. */
    private static final String FALLBACK = "document";

    /**
     * A PDF filename derived from a document title, safe for the version store.
     *
     * <pre>
     *   derivedPdf("Testing document.pdf", "signed") -> "Testing-document-signed.pdf"
     *   derivedPdf("Invoice #24 -- Q1",    "reviewed") -> "Invoice-24-Q1-reviewed.pdf"
     *   derivedPdf("报告.pdf",              "signed") -> "document-signed.pdf"
     * </pre>
     *
     * @param title  the document's title, which may be null, blank or anything a user typed
     * @param suffix what was done to it - "signed", "reviewed" - appended before the extension
     */
    public static String derivedPdf(String title, String suffix) {
        return slug(title) + "-" + suffix + ".pdf";
    }

    /** The title reduced to characters the version store will accept. */
    private static String slug(String title) {

        String base = title == null ? "" : title.trim();

        // Drop a trailing extension of any supported kind - the copy is always
        // a PDF, and "report.docx-signed.pdf" reads as a mistake. The length
        // guard keeps a title like "v1.2 release notes" intact, where the dot
        // begins a version number rather than an extension.
        int dot = base.lastIndexOf('.');
        if (dot > 0 && base.length() - dot <= 6) {
            base = base.substring(0, dot);
        }

        base = base.replaceAll("[^A-Za-z0-9_-]+", "-")  // spaces, punctuation, accents
                   .replaceAll("-{2,}", "-")            // no runs of dashes
                   .replaceAll("^-+|-+$", "");          // and none at either end

        if (base.length() > MAX_BASE_LENGTH) {
            base = base.substring(0, MAX_BASE_LENGTH).replaceAll("-+$", "");
        }

        return base.isBlank() ? FALLBACK : base;
    }
}
