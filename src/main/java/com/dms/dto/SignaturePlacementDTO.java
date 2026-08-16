package com.dms.dto;

/**
 * One signature stamp positioned on a page.
 *
 * Coordinates are normalised fractions of the page (0.0 - 1.0) measured from the
 * TOP-LEFT corner, which is how the browser sees the rendered page. The backend
 * converts them to PDF user space (origin bottom-left) when stamping, so the
 * result is independent of whatever zoom level the user was working at.
 */
public record SignaturePlacementDTO(
        int page,          // 1-based page number
        double x,          // left edge, fraction of page width
        double y,          // top edge, fraction of page height
        double width,      // fraction of page width
        double height,     // fraction of page height
        String imageDataUrl // "data:image/png;base64,..."
) {}
