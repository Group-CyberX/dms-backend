package com.dms.service;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
public class MetadataExtractorService {

    /**
     * Attempts to extract text using Tesseract OCR for images or Apache PDFBox for PDFs.
     */
    public String extractText(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null) return "";

        try {
            return extractTextFromBytes(file.getBytes(), contentType, file.getOriginalFilename());
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    public String extractTextFromBytes(byte[] fileBytes, String contentType, String originalFilename) {
        if (contentType == null) return "";

        if (contentType.equals("application/pdf")) {
            return extractTextFromPdfBytes(fileBytes);
        } else if (contentType.startsWith("image/")) {
            return extractTextFromImageOcrBytes(fileBytes, originalFilename);
        }
        return "";
    }

    private String extractTextFromImageOcrBytes(byte[] fileBytes, String originalFilename) {
        File tempFile = null;
        try {
            tempFile = File.createTempFile("ocr_", originalFilename != null ? originalFilename : "image.png");
            Files.write(tempFile.toPath(), fileBytes);

            Tesseract tesseract = new Tesseract();
            // Pointing to the Homebrew installation of Tesseract on MacOS
            tesseract.setDatapath("/opt/homebrew/share/tessdata");
            String result = tesseract.doOCR(tempFile);
            return result != null ? result.trim() : "";
        } catch (TesseractException | IOException e) {
            e.printStackTrace();
            return "OCR Processing Failed: " + e.getMessage();
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    private String extractTextFromPdfBytes(byte[] fileBytes) {
        try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(fileBytes)) {
            PDFTextStripper pdfStripper = new PDFTextStripper();
            String text = pdfStripper.getText(document);
            return text != null ? text.trim() : "";
        } catch (IOException e) {
            e.printStackTrace();
            return "PDF Extraction Failed: " + e.getMessage();
        }
    }

    /**
     * Checks if a PDF file has any digital signatures.
     */
    public boolean hasDigitalSignature(MultipartFile file) {
        if (file.getContentType() != null && file.getContentType().equals("application/pdf")) {
            try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(file.getBytes())) {
                return document.getSignatureDictionaries() != null && !document.getSignatureDictionaries().isEmpty();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }
}
