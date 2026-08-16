package com.dms.service;

import com.dms.dto.SignaturePlacementDTO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;

/**
 * Burns signature images into a PDF at caller-supplied positions.
 *
 * Coordinate translation is the whole job here. The browser reports placements
 * as fractions of the page measured from the top-left; PDF user space has its
 * origin at the bottom-left with Y increasing upwards. Working in fractions
 * rather than pixels means the stamp lands in the same spot regardless of the
 * zoom level the approver happened to be using.
 */
@Service
public class PdfSignatureStampingService {

    /**
     * @return the stamped PDF as a byte array; the input is never modified.
     */
    public byte[] stamp(byte[] pdfBytes, List<SignaturePlacementDTO> placements) throws IOException {
        if (placements == null || placements.isEmpty()) {
            throw new IllegalArgumentException("At least one signature placement is required");
        }

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            int pageCount = document.getNumberOfPages();

            for (SignaturePlacementDTO placement : placements) {
                if (placement.page() < 1 || placement.page() > pageCount) {
                    throw new IllegalArgumentException(
                            "Placement refers to page " + placement.page()
                                    + " but the document has " + pageCount + " page(s)");
                }

                PDPage page = document.getPage(placement.page() - 1);
                byte[] imageBytes = decodeDataUrl(placement.imageDataUrl());
                PDImageXObject image = PDImageXObject.createFromByteArray(
                        document, imageBytes, "signature");

                Rect target = toPdfSpace(page, placement);

                // APPEND + preserve graphics state so we draw on top of existing content
                // without disturbing it.
                try (PDPageContentStream content = new PDPageContentStream(
                        document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    content.drawImage(image, target.x, target.y, target.width, target.height);
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    /** Number of pages, used to validate placements before any work is done. */
    public int pageCount(byte[] pdfBytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            return document.getNumberOfPages();
        }
    }

    /**
     * Converts a top-left-origin fractional placement into PDF user space.
     *
     * Rotation note: pages with /Rotate 90 or 270 have their visual width and
     * height swapped relative to the media box. We compensate by measuring the
     * fractions against the visible dimensions.
     */
    private Rect toPdfSpace(PDPage page, SignaturePlacementDTO placement) {
        PDRectangle box = page.getMediaBox();
        int rotation = ((page.getRotation() % 360) + 360) % 360;

        boolean swapped = rotation == 90 || rotation == 270;
        float visibleWidth = swapped ? box.getHeight() : box.getWidth();
        float visibleHeight = swapped ? box.getWidth() : box.getHeight();

        float w = (float) (placement.width() * visibleWidth);
        float h = (float) (placement.height() * visibleHeight);
        float x = (float) (placement.x() * visibleWidth) + box.getLowerLeftX();

        // Flip the Y axis: browser measures down from the top, PDF measures up
        // from the bottom, and drawImage positions by the image's lower-left corner.
        float y = box.getUpperRightY() - (float) (placement.y() * visibleHeight) - h;

        return new Rect(x, y, w, h);
    }

    /** Accepts either a full data URL or a bare base64 payload. */
    private byte[] decodeDataUrl(String dataUrl) {
        if (dataUrl == null || dataUrl.isBlank()) {
            throw new IllegalArgumentException("Signature image is missing");
        }
        int comma = dataUrl.indexOf(',');
        String base64 = (dataUrl.startsWith("data:") && comma > -1)
                ? dataUrl.substring(comma + 1)
                : dataUrl;
        try {
            return Base64.getDecoder().decode(base64.replaceAll("\\s", ""));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Signature image is not valid base64 data", e);
        }
    }

    private record Rect(float x, float y, float width, float height) {}
}
