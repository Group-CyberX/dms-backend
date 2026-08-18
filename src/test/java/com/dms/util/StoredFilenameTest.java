package com.dms.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The names given to copies the server produces - a signed document, or a
 * reviewed one.
 *
 * Both are built from the document's title and handed to the version store,
 * which accepts only letters, digits, dash and underscore. A title is free
 * text and routinely contains neither, so every case here ends with the same
 * assertion: whatever the title was, the store will take the result.
 *
 * A single space was enough to break this. An ordinary title like "Testing
 * document.pdf" failed after the PDF had already been stamped, with a message
 * about file names that neither the signer nor the reviewer could act on.
 */
class StoredFilenameTest {

    /** The rule DocumentVersionService enforces on any stored filename. */
    private static final Pattern SAFE_FILENAME = Pattern.compile(
            "^[A-Za-z0-9_-]+\\.(pdf|docx|xlsx|png|jpg|jpeg)$",
            Pattern.CASE_INSENSITIVE);

    /** Builds the name and fails unless the version store would accept it. */
    private String name(String title, String suffix) {
        String built = StoredFilename.derivedPdf(title, suffix);
        assertTrue(SAFE_FILENAME.matcher(built).matches(),
                "the version store would reject " + built);
        return built;
    }

    @Test
    @DisplayName("a title with spaces becomes a storable name, for either copy")
    void spacesAreFolded() {
        assertEquals("Testing-document-signed.pdf", name("Testing document.pdf", "signed"));
        assertEquals("Testing-document-reviewed.pdf", name("Testing document.pdf", "reviewed"));
    }

    @Test
    @DisplayName("the original extension is dropped rather than left in the middle")
    void extensionIsReplaced() {
        assertEquals("Quarterly-report-signed.pdf", name("Quarterly report.docx", "signed"));
    }

    @Test
    @DisplayName("punctuation collapses instead of producing runs of dashes")
    void punctuationCollapses() {
        assertEquals("Invoice-2024-Q1-signed.pdf", name("Invoice #2024 -- Q1!.pdf", "signed"));
    }

    @Test
    @DisplayName("a version number in the title is not mistaken for an extension")
    void shortTrailingSegmentIsKept() {
        assertEquals("v1-2-release-notes-signed.pdf", name("v1.2 release notes", "signed"));
    }

    @Test
    @DisplayName("a title with nothing storable in it still yields a name")
    void unstorableTitleFallsBack() {
        assertEquals("document-signed.pdf", name("报告.pdf", "signed"));
        assertEquals("document-signed.pdf", name("   ", "signed"));
        assertEquals("document-signed.pdf", name(null, "signed"));
    }

    @Test
    @DisplayName("a very long title is truncated, and not left ending in a dash")
    void longTitleIsTruncated() {
        String built = name("a b ".repeat(80), "reviewed");
        assertTrue(built.length() <= 100 + "-reviewed.pdf".length(), built);
        assertFalse(built.contains("--"), built);
    }
}
