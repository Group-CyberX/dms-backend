package com.dms.service;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MetadataExtractorService {

    private static final boolean TESSERACT_AVAILABLE = isLibraryAvailable("net.sourceforge.tess4j.Tesseract");
    private static final boolean PDFBOX_AVAILABLE = isLibraryAvailable("org.apache.pdfbox.pdmodel.PDDocument");

    private static boolean isLibraryAvailable(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Directories Tesseract's language data lives in, by platform. The first one
     * that exists on this machine wins, so the same build runs on a Windows
     * laptop and an Apple Silicon Mac without either developer editing code.
     * An explicit app.ocr.tessdata-path overrides the whole list.
     */
    private static final List<String> TESSDATA_CANDIDATES = List.of(
            "C:\\Program Files\\Tesseract-OCR\\tessdata",       // Windows (default installer)
            "C:\\Program Files (x86)\\Tesseract-OCR\\tessdata", // Windows (32-bit installer)
            "/opt/homebrew/share/tessdata",                     // macOS, Apple Silicon Homebrew
            "/usr/local/share/tessdata",                        // macOS, Intel Homebrew
            "/usr/share/tesseract-ocr/5/tessdata",              // Linux
            "/usr/share/tesseract-ocr/4.00/tessdata",
            "/usr/share/tessdata"
    );

    /** Native library directories JNA should search, by platform. */
    private static final List<String> JNA_CANDIDATES = List.of(
            "/opt/homebrew/lib",   // Apple Silicon Homebrew
            "/usr/local/lib",      // Intel Homebrew / Linux
            "/usr/lib"
    );

    static {
        // Only point JNA at a directory that actually exists. Setting it blindly
        // to a Homebrew path breaks the lookup on Windows, where the Tesseract
        // installer already puts its DLLs on the system PATH.
        for (String candidate : JNA_CANDIDATES) {
            if (new File(candidate).isDirectory()) {
                System.setProperty("jna.library.path", candidate);
                break;
            }
        }
    }

    /** Overrides the probe when set, for machines with a non-standard install. */
    @Value("${app.ocr.tessdata-path:}")
    private String configuredTessdataPath;

    /** Resolved once and reused; null means "no language data found". */
    private volatile String resolvedTessdataPath;
    private volatile boolean tessdataResolved = false;

    /** Resolve at startup so a misconfigured machine is obvious immediately. */
    @jakarta.annotation.PostConstruct
    void logOcrConfiguration() {
        resolveTessdataPath();
    }

    /**
     * Finds the tessdata directory for this machine, logging the outcome once so
     * a misconfigured environment is obvious at the first OCR attempt rather than
     * showing up as empty extracted text.
     */
    private String resolveTessdataPath() {
        if (tessdataResolved) {
            return resolvedTessdataPath;
        }

        synchronized (this) {
            if (tessdataResolved) {
                return resolvedTessdataPath;
            }

            String found = null;

            if (configuredTessdataPath != null && !configuredTessdataPath.isBlank()) {
                if (new File(configuredTessdataPath).isDirectory()) {
                    found = configuredTessdataPath;
                } else {
                    System.err.println("OCR: app.ocr.tessdata-path is set to '" + configuredTessdataPath
                            + "' but that directory does not exist - falling back to auto-detection.");
                }
            }

            if (found == null) {
                for (String candidate : TESSDATA_CANDIDATES) {
                    if (new File(candidate).isDirectory()) {
                        found = candidate;
                        break;
                    }
                }
            }

            if (found != null) {
                System.out.println("OCR: using tessdata at " + found);
            } else {
                System.err.println("OCR: no tessdata directory found on this machine (" + System.getProperty("os.name")
                        + "). Install Tesseract, or set app.ocr.tessdata-path. Uploads will still succeed without OCR text.");
            }

            resolvedTessdataPath = found;
            tessdataResolved = true;
            return found;
        }
    }

    /** Whether a tessdata directory was actually found on this machine - the real answer, not an assumed "running". */
    public boolean isOcrAvailable() {
        return resolveTessdataPath() != null;
    }

    public String extractText(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null) {
            return "";
        }

        try {
            return extractTextFromBytes(file.getBytes(), contentType, file.getOriginalFilename());
        } catch (IOException e) {
            return "";
        }
    }

    public String extractTextFromBytes(byte[] fileBytes, String contentType, String originalFilename) {
        if (contentType == null) {
            return "";
        }

        if ("application/pdf".equals(contentType)) {
            return extractTextFromPdfBytes(fileBytes);
        }

        if (contentType.startsWith("image/")) {
            return extractTextFromImageOcrBytes(fileBytes, originalFilename);
        }

        return "";
    }

    private String extractTextFromImageOcrBytes(byte[] fileBytes, String originalFilename) {
        if (!TESSERACT_AVAILABLE) {
            return "OCR not available on this platform";
        }

        String tessdataPath = resolveTessdataPath();
        if (tessdataPath == null) {
            // No language data installed. The document is still stored and
            // searchable by title and metadata - only the text layer is missing.
            return "OCR not available: Tesseract language data not installed on this server";
        }

        File tempFile = null;
        try {
            String suffix = originalFilename != null && originalFilename.contains(".")
                    ? originalFilename.substring(originalFilename.lastIndexOf('.'))
                    : ".img";
            tempFile = File.createTempFile("ocr_", suffix);
            Files.write(tempFile.toPath(), fileBytes);

            Class<?> tesseractClass = Class.forName("net.sourceforge.tess4j.Tesseract");
            Object tesseract = tesseractClass.getDeclaredConstructor().newInstance();

            Method setDatapath = tesseractClass.getMethod("setDatapath", String.class);
            setDatapath.invoke(tesseract, tessdataPath);

            Method doOcr = tesseractClass.getMethod("doOCR", File.class);
            String result = (String) doOcr.invoke(tesseract, tempFile);
            return result != null ? result.trim() : "";
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            System.err.println("OCR Error (Invocation): " + (cause != null ? cause.getMessage() : "null"));
            if (cause != null) cause.printStackTrace();
            throw new RuntimeException("OCR Processing Failed: " + (cause != null ? cause.getMessage() : "null"), cause);
        } catch (Throwable t) {
            System.err.println("OCR Error: " + t.getMessage());
            t.printStackTrace();
            throw new RuntimeException("OCR Processing Failed: " + t.getMessage(), t);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    private String extractTextFromPdfBytes(byte[] fileBytes) {
        if (!PDFBOX_AVAILABLE) {
            return "PDF text extraction not available on this platform";
        }

        Object document = null;
        try {
            Class<?> loaderClass = Class.forName("org.apache.pdfbox.Loader");
            Method loadPdf = loaderClass.getMethod("loadPDF", byte[].class);
            document = loadPdf.invoke(null, (Object) fileBytes);

            Class<?> pdfDocClass = Class.forName("org.apache.pdfbox.pdmodel.PDDocument");
            Class<?> stripperClass = Class.forName("org.apache.pdfbox.text.PDFTextStripper");
            Object stripper = stripperClass.getDeclaredConstructor().newInstance();

            Method getText = stripperClass.getMethod("getText", pdfDocClass);
            String text = (String) getText.invoke(stripper, document);
            return text != null ? text.trim() : "";
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            throw new RuntimeException("PDF Extraction Failed: " + (cause != null ? cause.getMessage() : "null"), cause);
        } catch (Exception e) {
            throw new RuntimeException("PDF Extraction Failed: " + e.getMessage(), e);
        } finally {
            if (document != null) {
                try {
                    Method close = document.getClass().getMethod("close");
                    close.invoke(document);
                } catch (Exception ignored) {
                    // no-op
                }
            }
        }
    }

    public boolean hasDigitalSignature(MultipartFile file) {
        if (!PDFBOX_AVAILABLE) {
            return false;
        }

        if (!"application/pdf".equals(file.getContentType())) {
            return false;
        }

        Object document = null;
        try {
            Class<?> loaderClass = Class.forName("org.apache.pdfbox.Loader");
            Method loadPdf = loaderClass.getMethod("loadPDF", byte[].class);
            document = loadPdf.invoke(null, (Object) file.getBytes());

            Method getSignatures = document.getClass().getMethod("getSignatureDictionaries");
            Object signatures = getSignatures.invoke(document);
            return signatures instanceof List<?> && !((List<?>) signatures).isEmpty();
        } catch (Exception e) {
            return false;
        } finally {
            if (document != null) {
                try {
                    Method close = document.getClass().getMethod("close");
                    close.invoke(document);
                } catch (Exception ignored) {
                    // no-op
                }
            }
        }
    }
}
