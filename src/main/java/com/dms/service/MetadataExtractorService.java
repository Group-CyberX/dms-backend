package com.dms.service;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.List;
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

    static {
        // Force JNA to look in Homebrew's lib folder for Tesseract on Apple Silicon Macs
        System.setProperty("jna.library.path", "/opt/homebrew/lib");
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
            setDatapath.invoke(tesseract, "/opt/homebrew/share/tessdata");

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
