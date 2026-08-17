package com.dms.service;

import com.dms.dto.SignaturePlacementDTO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The risky part of signing is the coordinate flip: the browser reports
 * placements from the top-left, PDF user space measures up from the bottom-left.
 * These tests stamp a solid black block at a known spot and then rasterise the
 * result to check the ink actually landed where it was asked to.
 */
class PdfSignatureStampingServiceTest {

    private PdfSignatureStampingService service;

    @BeforeEach
    void setUp() {
        service = new PdfSignatureStampingService();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** A blank single-page A4 PDF. */
    private byte[] blankA4() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage(PDRectangle.A4));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    /** A solid black PNG encoded as a data URL. */
    private String blackSquareDataUrl() throws IOException {
        BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, 40, 40);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
    }

    /** True when the given fractional point of the rendered page is dark. */
    private boolean isDarkAt(BufferedImage rendered, double fx, double fy) {
        int px = (int) Math.round(fx * rendered.getWidth());
        int py = (int) Math.round(fy * rendered.getHeight());
        px = Math.min(Math.max(px, 0), rendered.getWidth() - 1);
        py = Math.min(Math.max(py, 0), rendered.getHeight() - 1);

        Color c = new Color(rendered.getRGB(px, py));
        return (c.getRed() + c.getGreen() + c.getBlue()) / 3 < 128;
    }

    private BufferedImage renderFirstPage(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFRenderer(doc).renderImage(0, 2f);
        }
    }

    // ------------------------------------------------------------------
    // tests
    // ------------------------------------------------------------------

    @Test
    @DisplayName("stamps the signature in the upper area when y is near the top")
    void stampsNearTopWhenYIsSmall() throws IOException {
        // A band across the top tenth of the page.
        SignaturePlacementDTO placement = new SignaturePlacementDTO(
                1, 0.30, 0.05, 0.40, 0.08, blackSquareDataUrl());

        byte[] stamped = service.stamp(blankA4(), List.of(placement));
        BufferedImage rendered = renderFirstPage(stamped);

        // Centre of the requested box should be inked...
        assertTrue(isDarkAt(rendered, 0.50, 0.09),
                "expected the signature near the top of the page");
        // ...and the bottom of the page should be untouched. This is the
        // assertion that actually catches an inverted Y axis.
        assertFalse(isDarkAt(rendered, 0.50, 0.90),
                "bottom of the page should be blank - Y axis is flipped");
    }

    @Test
    @DisplayName("stamps the signature in the lower area when y is near the bottom")
    void stampsNearBottomWhenYIsLarge() throws IOException {
        SignaturePlacementDTO placement = new SignaturePlacementDTO(
                1, 0.30, 0.85, 0.40, 0.08, blackSquareDataUrl());

        byte[] stamped = service.stamp(blankA4(), List.of(placement));
        BufferedImage rendered = renderFirstPage(stamped);

        assertTrue(isDarkAt(rendered, 0.50, 0.89),
                "expected the signature near the bottom of the page");
        assertFalse(isDarkAt(rendered, 0.50, 0.10),
                "top of the page should be blank");
    }

    @Test
    @DisplayName("keeps the original page count and leaves the input untouched")
    void preservesDocumentStructure() throws IOException {
        byte[] original = blankA4();
        byte[] before = original.clone();

        byte[] stamped = service.stamp(original,
                List.of(new SignaturePlacementDTO(1, 0.1, 0.1, 0.2, 0.05, blackSquareDataUrl())));

        assertArrayEquals(before, original, "input array must not be modified");
        assertEquals(1, service.pageCount(stamped));
        assertNotEquals(0, stamped.length);
    }

    @Test
    @DisplayName("applies every placement in the list")
    void appliesMultiplePlacements() throws IOException {
        String sig = blackSquareDataUrl();
        byte[] stamped = service.stamp(blankA4(), List.of(
                new SignaturePlacementDTO(1, 0.05, 0.05, 0.20, 0.06, sig),
                new SignaturePlacementDTO(1, 0.05, 0.85, 0.20, 0.06, sig)
        ));

        BufferedImage rendered = renderFirstPage(stamped);
        assertTrue(isDarkAt(rendered, 0.14, 0.08), "first placement missing");
        assertTrue(isDarkAt(rendered, 0.14, 0.88), "second placement missing");
    }

    @Test
    @DisplayName("rejects a placement pointing past the last page")
    void rejectsOutOfRangePage() throws IOException {
        byte[] pdf = blankA4();
        List<SignaturePlacementDTO> placements =
                List.of(new SignaturePlacementDTO(7, 0.1, 0.1, 0.2, 0.05, blackSquareDataUrl()));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.stamp(pdf, placements));
        assertTrue(ex.getMessage().contains("page 7"));
    }

    @Test
    @DisplayName("rejects an empty placement list")
    void rejectsEmptyPlacements() throws IOException {
        byte[] pdf = blankA4();
        assertThrows(IllegalArgumentException.class, () -> service.stamp(pdf, List.of()));
        assertThrows(IllegalArgumentException.class, () -> service.stamp(pdf, null));
    }

    @Test
    @DisplayName("rejects a signature image that is not valid base64")
    void rejectsBadImageData() throws IOException {
        byte[] pdf = blankA4();
        List<SignaturePlacementDTO> placements =
                List.of(new SignaturePlacementDTO(1, 0.1, 0.1, 0.2, 0.05, "data:image/png;base64,!!!not-base64!!!"));

        assertThrows(IllegalArgumentException.class, () -> service.stamp(pdf, placements));
    }

    @Test
    @DisplayName("accepts a bare base64 payload without the data URL prefix")
    void acceptsBareBase64() throws IOException {
        String withPrefix = blackSquareDataUrl();
        String bare = withPrefix.substring(withPrefix.indexOf(',') + 1);

        byte[] stamped = service.stamp(blankA4(),
                List.of(new SignaturePlacementDTO(1, 0.3, 0.4, 0.4, 0.1, bare)));

        assertEquals(1, service.pageCount(stamped));
    }
}
