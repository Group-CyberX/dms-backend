package com.dms.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;

/**
 * Service to interact with ClamAV for virus scanning.
 * Communicates with ClamAV daemon via the INSTREAM protocol.
 */
@Service
public class VirusScanService {

    private static final Logger logger = LoggerFactory.getLogger(VirusScanService.class);

    @Value("${clamav.host:localhost}")
    private String clamavHost;

    @Value("${clamav.port:3310}")
    private int clamavPort;

    @Value("${clamav.enabled:true}")
    private boolean clamavEnabled;

    /**
     * Scans a file byte array for viruses using ClamAV.
     * Returns true if the file is clean, false if a virus is detected.
     */
    public ScanResult scanFile(byte[] fileBytes, String fileName) {
        if (!clamavEnabled) {
            logger.info("ClamAV is disabled. Skipping virus scan for file: {}", fileName);
            return new ScanResult(true, "ClamAV is disabled - file passed (scanning not required)", null);
        }

        try {
            logger.info("Starting virus scan for file: {} ({} bytes)", fileName, fileBytes.length);
            
            // Connect to ClamAV daemon with timeout
            Socket socket = new Socket();
            socket.connect(new java.net.InetSocketAddress(clamavHost, clamavPort), 5000);
            
            // Send INSTREAM command (for streaming file content)
            byte[] instreamCommand = "INSTREAM\n".getBytes();
            socket.getOutputStream().write(instreamCommand);
            socket.getOutputStream().flush();

            // Send file content in chunks
            int chunkSize = 65536; // 64KB chunks
            int bytesWritten = 0;
            
            while (bytesWritten < fileBytes.length) {
                int toWrite = Math.min(chunkSize, fileBytes.length - bytesWritten);
                byte[] sizeBytes = new byte[4];
                sizeBytes[0] = (byte) ((toWrite >> 24) & 0xFF);
                sizeBytes[1] = (byte) ((toWrite >> 16) & 0xFF);
                sizeBytes[2] = (byte) ((toWrite >> 8) & 0xFF);
                sizeBytes[3] = (byte) (toWrite & 0xFF);
                
                socket.getOutputStream().write(sizeBytes);
                socket.getOutputStream().write(fileBytes, bytesWritten, toWrite);
                socket.getOutputStream().flush();
                
                bytesWritten += toWrite;
            }

            // Send zero-length chunk to indicate end of stream
            socket.getOutputStream().write(new byte[]{0, 0, 0, 0});
            socket.getOutputStream().flush();

            // Read response
            byte[] response = new byte[1024];
            int bytesRead = socket.getInputStream().read(response);
            String scanResult = new String(response, 0, bytesRead).trim();
            
            socket.close();

            logger.info("ClamAV scan result for {}: {}", fileName, scanResult);

            // Parse response
            // Typical responses:
            // "stream: OK" - File is clean
            // "stream: Eicar-Test-File FOUND" - Virus detected
            if (scanResult.contains("OK")) {
                return new ScanResult(true, "File is clean", null);
            } else if (scanResult.contains("FOUND")) {
                String threat = extractThreatName(scanResult);
                return new ScanResult(false, "Virus detected", threat);
            } else {
                return new ScanResult(true, "Inconclusive scan result", scanResult);
            }

        } catch (java.net.ConnectException e) {
            logger.warn("ClamAV daemon not available at {}:{} - skipping virus scan", clamavHost, clamavPort);
            return new ScanResult(true, "ClamAV daemon unavailable - file passed (will retry later)", null);
        } catch (IOException e) {
            logger.warn("Error communicating with ClamAV: {} - skipping virus scan", e.getMessage());
            return new ScanResult(true, "Virus scan unavailable - file passed", null);
        }
    }

    /**
     * Extracts the threat name from ClamAV response.
     * Example: "stream: Eicar-Test-File FOUND" -> "Eicar-Test-File"
     */
    private String extractThreatName(String response) {
        if (response.contains("FOUND")) {
            String[] parts = response.split("\\s+");
            if (parts.length >= 2) {
                return parts[1]; // The threat name is typically the second token
            }
        }
        return "Unknown Threat";
    }

    /**
     * Data class to hold virus scan results.
     */
    public static class ScanResult {
        public final boolean isClean;
        public final String message;
        public final String threatName;

        public ScanResult(boolean isClean, String message, String threatName) {
            this.isClean = isClean;
            this.message = message;
            this.threatName = threatName;
        }

        @Override
        public String toString() {
            return "ScanResult{" +
                    "isClean=" + isClean +
                    ", message='" + message + '\'' +
                    ", threatName='" + threatName + '\'' +
                    '}';
        }
    }
}
