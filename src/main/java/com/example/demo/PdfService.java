package com.example.demo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PdfService {

    private final Path storageDirectory = Paths.get(System.getProperty("java.io.tmpdir"), "dude_converter");

    public PdfService() throws IOException {
        if (!Files.exists(storageDirectory)) {
            Files.createDirectories(storageDirectory);
        }
    }

    public File mergePdfs(List<MultipartFile> files) throws IOException {
        String sessionToken = UUID.randomUUID().toString();
        List<File> temporaryFiles = new ArrayList<>();
        PDFMergerUtility mergerUtility = new PDFMergerUtility();
        String outputFileName = "merged_" + sessionToken + ".pdf";
        File outputFile = storageDirectory.resolve(outputFileName).toFile();
        mergerUtility.setDestinationFileName(outputFile.getAbsolutePath());
        try {
            for (MultipartFile file : files) {
                if (file.isEmpty()) continue;
                String stagingName = "stage_" + UUID.randomUUID() + "_" + file.getOriginalFilename();
                File stagedFile = storageDirectory.resolve(stagingName).toFile();
                file.transferTo(stagedFile);
                temporaryFiles.add(stagedFile);
                mergerUtility.addSource(stagedFile);
            }
            mergerUtility.mergeDocuments(null);
            return outputFile;
        } finally {
            for (File temp : temporaryFiles) {
                if (temp.exists()) temp.delete();
            }
        }
    }

    public File splitPdf(MultipartFile file) throws IOException {
        String sessionToken = UUID.randomUUID().toString();
        File inputFile = storageDirectory.resolve("to_split_" + sessionToken + ".pdf").toFile();
        file.transferTo(inputFile);
        File zipFile = storageDirectory.resolve("split_" + sessionToken + ".zip").toFile();
        try (PDDocument document = Loader.loadPDF(inputFile);
             ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            Splitter splitter = new Splitter();
            List<PDDocument> pages = splitter.split(document);
            int pageNumber = 1;
            for (PDDocument page : pages) {
                zos.putNextEntry(new ZipEntry("page_" + pageNumber + ".pdf"));
                page.save(zos);
                zos.closeEntry();
                page.close();
                pageNumber++;
            }
            return zipFile;
        } finally {
            if (inputFile.exists()) inputFile.delete();
        }
    }

    public File convertPdfToTxt(MultipartFile file) throws IOException {
        String sessionToken = UUID.randomUUID().toString();
        File inputFile = storageDirectory.resolve("to_txt_" + sessionToken + ".pdf").toFile();
        file.transferTo(inputFile);
        File txtFile = storageDirectory.resolve("converted_" + sessionToken + ".txt").toFile();
        try (PDDocument document = Loader.loadPDF(inputFile);
             FileWriter writer = new FileWriter(txtFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            writer.write(stripper.getText(document));
            return txtFile;
        } finally {
            if (inputFile.exists()) inputFile.delete();
        }
    }

    public File convertDocxToPdf(MultipartFile file) throws IOException {
        String sessionToken = UUID.randomUUID().toString();
        File inputFile = storageDirectory.resolve("to_pdf_" + sessionToken + ".docx").toFile();
        file.transferTo(inputFile);
        File pdfFile = storageDirectory.resolve("converted_" + sessionToken + ".pdf").toFile();
        try (FileInputStream in = new FileInputStream(inputFile);
             XWPFDocument docx = new XWPFDocument(in);
             PDDocument pdf = new PDDocument()) {
            PDPage page = new PDPage();
            pdf.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDPageContentStream contentStream = new PDPageContentStream(pdf, page);
            contentStream.beginText();
            contentStream.setFont(font, 12);
            contentStream.newLineAtOffset(50, 750);
            int lineCount = 0;
            for (XWPFParagraph paragraph : docx.getParagraphs()) {
                String text = paragraph.getText();
                if (text.trim().isEmpty()) continue;
                if (lineCount > 40) {
                    contentStream.endText(); contentStream.close();
                    page = new PDPage(); pdf.addPage(page);
                    contentStream = new PDPageContentStream(pdf, page);
                    contentStream.beginText(); contentStream.setFont(font, 12);
                    contentStream.newLineAtOffset(50, 750); lineCount = 0;
                }
                String safeLine = text.replace("\t", "    ").replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "");
                try { contentStream.showText(safeLine); } catch (IllegalArgumentException e) { contentStream.showText(" "); }
                contentStream.newLineAtOffset(0, -18); lineCount++;
            }
            contentStream.endText(); contentStream.close();
            pdf.save(pdfFile);
            return pdfFile;
        } finally {
            if (inputFile.exists()) inputFile.delete();
        }
    }

    public File convertTxtToPdf(MultipartFile file) throws IOException {
        String sessionToken = UUID.randomUUID().toString();
        File txtFile = storageDirectory.resolve("source_" + sessionToken + ".txt").toFile();
        file.transferTo(txtFile);
        File pdfFile = storageDirectory.resolve("converted_" + sessionToken + ".pdf").toFile();
        try (PDDocument doc = new PDDocument();
             BufferedReader br = new BufferedReader(new FileReader(txtFile, StandardCharsets.UTF_8))) {
            PDPage page = new PDPage(); doc.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDPageContentStream contents = new PDPageContentStream(doc, page);
            contents.beginText(); contents.setFont(font, 12);
            contents.newLineAtOffset(50, 750);
            String line; int lineCount = 0;
            while ((line = br.readLine()) != null) {
                if (lineCount > 45) {
                    contents.endText(); contents.close();
                    page = new PDPage(); doc.addPage(page);
                    contents = new PDPageContentStream(doc, page);
                    contents.beginText(); contents.setFont(font, 12);
                    contents.newLineAtOffset(50, 750); lineCount = 0;
                }
                String safeLine = line.replace("\t", "    ").replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "");
                try { contents.showText(safeLine); } catch (IllegalArgumentException e) { contents.showText(" "); }
                contents.newLineAtOffset(0, -15); lineCount++;
            }
            contents.endText(); contents.close(); doc.save(pdfFile);
            return pdfFile;
        } finally {
            if (txtFile.exists()) txtFile.delete();
        }
    }

    public File convertPdfToDocx(MultipartFile file) throws IOException {
        String sessionToken = UUID.randomUUID().toString();
        File inputFile = storageDirectory.resolve("src_" + sessionToken + ".pdf").toFile();
        file.transferTo(inputFile);
        File docxFile = storageDirectory.resolve("converted_" + sessionToken + ".docx").toFile();
        try (PDDocument document = Loader.loadPDF(inputFile);
             XWPFDocument docx = new XWPFDocument();
             FileOutputStream out = new FileOutputStream(docxFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String[] lines = stripper.getText(document).split("\\r?\\n");
            for (String line : lines) {
                XWPFParagraph paragraph = docx.createParagraph();
                XWPFRun run = paragraph.createRun();
                run.setText(line);
            }
            docx.write(out); return docxFile;
        } finally {
            if (inputFile.exists()) inputFile.delete();
        }
    }

    public File compressUniversalFile(MultipartFile file, String level, int targetSizeMb) throws IOException {
        String sessionToken = UUID.randomUUID().toString();
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        File compressedFile = storageDirectory.resolve("optimized_" + sessionToken + "_" + originalName).toFile();

        if (originalName.toLowerCase().endsWith(".pdf")) {
            File tempIn = storageDirectory.resolve("temp_" + sessionToken + ".pdf").toFile();
            file.transferTo(tempIn);
            try (PDDocument doc = Loader.loadPDF(tempIn)) {
                doc.save(compressedFile);
                return compressedFile;
            } finally {
                if (tempIn.exists()) tempIn.delete();
            }
        }

        try (InputStream is = file.getInputStream();
             OutputStream os = new FileOutputStream(compressedFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
        }
        return compressedFile;
    }

    public Path getStorageDirectory() { return this.storageDirectory; }
}