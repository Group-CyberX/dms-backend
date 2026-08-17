package com.dms.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * A stand-in ERP.
 *
 * This is NOT part of the DMS. It exists so the connector has a real REST
 * service to talk to during development and demonstration, in place of an SAP
 * or Oracle tenant we cannot obtain. The connector reaches it over HTTP using
 * the base URL stored against a connection, exactly as it would reach a real
 * system - so pointing a connection at a genuine ERP is a configuration change,
 * not a code change.
 *
 * The field names here are deliberately un-DMS-like (PurchaseOrderNo, not
 * poNumber). That is the point: the integration_mappings table has to translate
 * them, which is what makes the connector universal rather than hard-wired.
 */
@RestController
@RequestMapping("/mock-erp")
public class MockErpController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "status", "UP",
                "system", "MOCK-ERP",
                "version", "1.0"
        );
    }

    @GetMapping("/purchase-orders")
    public List<Map<String, Object>> purchaseOrders() {
        return List.of(
                Map.of(
                        "PurchaseOrderNo", "PO-2026-0042",
                        "VendorName", "ABC Corporation",
                        "TotalAmount", 12450.00,
                        "Currency", "USD",
                        "OrderDate", "2026-02-05",
                        "Status", "OPEN"
                ),
                Map.of(
                        "PurchaseOrderNo", "PO-2026-0043",
                        "VendorName", "Lanka Supplies (Pvt) Ltd",
                        "TotalAmount", 320000.00,
                        "Currency", "LKR",
                        "OrderDate", "2026-02-11",
                        "Status", "APPROVED"
                ),
                Map.of(
                        "PurchaseOrderNo", "PO-2026-0051",
                        "VendorName", "Ceylon Paper Mills",
                        "TotalAmount", 87500.00,
                        "Currency", "LKR",
                        "OrderDate", "2026-03-02",
                        "Status", "OPEN"
                )
        );
    }

    @GetMapping("/invoices")
    public List<Map<String, Object>> invoices() {
        return List.of(
                Map.of(
                        "InvoiceNo", "INV-2026-Q1-001",
                        "PurchaseOrderNo", "PO-2026-0042",
                        "VendorName", "ABC Corporation",
                        "TotalAmount", 12450.00,
                        "Currency", "USD",
                        "DueDate", "2026-03-05",
                        "Status", "AWAITING_PAYMENT"
                ),
                Map.of(
                        "InvoiceNo", "INV-2026-Q1-014",
                        "PurchaseOrderNo", "PO-2026-0043",
                        "VendorName", "Lanka Supplies (Pvt) Ltd",
                        "TotalAmount", 320000.00,
                        "Currency", "LKR",
                        "DueDate", "2026-03-18",
                        "Status", "PAID"
                )
        );
    }
}
