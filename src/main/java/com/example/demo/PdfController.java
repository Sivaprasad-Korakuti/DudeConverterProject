package com.example.demo;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class PdfController {

    private final PdfService pdfService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public PdfController(PdfService pdfService, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.pdfService = pdfService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/")
    public String index(Model model, @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails != null) {
            model.addAttribute("username", userDetails.getUsername());
        }
        return "index";
    }

    @GetMapping("/login")
    public String login() { return "login"; }

    @GetMapping("/signup")
    public String signupPage() { return "signup"; }

    @PostMapping("/signup")
    public String registerUser(@RequestParam("username") String username, @RequestParam("password") String password, Model model) {
        if (userRepository.findByUsername(username).isPresent()) {
            model.addAttribute("error", "Username is already taken!");
            return "signup";
        }
        UserAccount account = new UserAccount();
        account.setUsername(username);
        account.setPassword(passwordEncoder.encode(password));
        userRepository.save(account);
        return "redirect:/login";
    }

    @GetMapping("/about")
    public String aboutPage() { return "about"; }

    @PostMapping("/merge")
    public ResponseEntity<Resource> handlePdfMerge(@RequestParam("files") List<MultipartFile> files) throws IOException {
        if (files == null || files.isEmpty() || files.get(0).isEmpty()) return ResponseEntity.badRequest().build();
        File resultFile = pdfService.mergePdfs(files);
        return createResponse(resultFile, "merged-documents.pdf", MediaType.APPLICATION_PDF);
    }

    @PostMapping("/split")
    public ResponseEntity<Resource> handlePdfSplit(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) return ResponseEntity.badRequest().build();
        File resultZip = pdfService.splitPdf(file);
        return createResponse(resultZip, "split-pages.zip", MediaType.APPLICATION_OCTET_STREAM);
    }

    @PostMapping("/pdf-to-txt")
    public ResponseEntity<Resource> handlePdfToTxt(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) return ResponseEntity.badRequest().build();
        File resultTxt = pdfService.convertPdfToTxt(file);
        return createResponse(resultTxt, "converted-text.txt", MediaType.TEXT_PLAIN);
    }

    @PostMapping("/docx-to-pdf")
    public ResponseEntity<Resource> handleDocxToPdf(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) return ResponseEntity.badRequest().build();
        File resultPdf = pdfService.convertDocxToPdf(file);
        return createResponse(resultPdf, "converted-doc.pdf", MediaType.APPLICATION_PDF);
    }

    @PostMapping("/txt-to-pdf")
    public ResponseEntity<Resource> handleTxtToPdf(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) return ResponseEntity.badRequest().build();
        File resultPdf = pdfService.convertTxtToPdf(file);
        return createResponse(resultPdf, "text-converted.pdf", MediaType.APPLICATION_PDF);
    }

    @PostMapping("/pdf-to-docx")
    public ResponseEntity<Resource> handlePdfToDocx(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) return ResponseEntity.badRequest().build();
        File resultDocx = pdfService.convertPdfToDocx(file);
        return createResponse(resultDocx, "pdf-converted.docx", MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
    }

    @PostMapping("/compress")
    public ResponseEntity<Resource> handlePdfCompression(
            @RequestParam("file") MultipartFile file,
            @RequestParam("reductionLevel") String reductionLevel,
            @RequestParam("targetSizeMb") int targetSizeMb) throws IOException {
        if (file == null || file.isEmpty()) return ResponseEntity.badRequest().build();
        File resultFile = pdfService.compressUniversalFile(file, reductionLevel, targetSizeMb);
        return createResponse(resultFile, "optimized_" + file.getOriginalFilename(), MediaType.APPLICATION_OCTET_STREAM);
    }

    private ResponseEntity<Resource> createResponse(File file, String filename, MediaType contentType) {
        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(contentType)
                .contentLength(file.length())
                .body(resource);
    }
}