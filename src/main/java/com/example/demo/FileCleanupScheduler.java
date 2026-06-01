package com.example.demo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class FileCleanupScheduler {

    private final PdfService pdfService;

    public FileCleanupScheduler(PdfService pdfService) {
        this.pdfService = pdfService;
    }

    // Runs once every 15 minutes
    @Scheduled(fixedRate = 900000)
    public void purgeOldPdfFiles() throws IOException {
        Path targetDir = pdfService.getStorageDirectory();
        Instant expirationThreshold = Instant.now().minus(30, ChronoUnit.MINUTES);

        Files.list(targetDir).forEach(filePath -> {
            try {
                BasicFileAttributes attributes = Files.readAttributes(filePath, BasicFileAttributes.class);
                Instant fileAge = attributes.creationTime().toInstant();

                if (fileAge.isBefore(expirationThreshold)) {
                    Files.deleteIfExists(filePath);
                }
            } catch (IOException e) {
                System.err.println("Failed to clear expired file: " + filePath.getFileName() + " -> " + e.getMessage());
            }
        });
    }
}