package com.dms;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class DmsBackendApplication {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public static void main(String[] args) {
        // Point JNA to the Homebrew library path to find Tesseract
        System.setProperty("jna.library.path", "/opt/homebrew/lib");
        SpringApplication.run(DmsBackendApplication.class, args);
    }

    @PostConstruct
    public void fixDatabaseSchema() {
        try {
            // Force the ocr_content column to be type TEXT instead of VARCHAR(255)
            // since Hibernate's update doesn't automatically change existing column types.
            jdbcTemplate.execute("ALTER TABLE \"DocumentVersion\" ALTER COLUMN ocr_content TYPE TEXT;");
            System.out.println("✅ Successfully altered ocr_content column type to TEXT");
        } catch (Exception e) {
            System.out.println("⚠️ Could not alter column type: " + e.getMessage());
        }
    }
}
