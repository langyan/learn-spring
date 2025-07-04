package com.lin.spring.file.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/upload")
public class ChunkedUploadController {

    private final Path storagePath = Paths.get("uploads");

    @PostMapping("/chunk")
    public ResponseEntity<String> uploadChunk(
            @RequestParam("file") MultipartFile chunk,
            @RequestParam("chunkNumber") int chunkNumber,
            @RequestParam("totalChunks") int totalChunks,
            @RequestParam("fileName") String fileName
    ) throws IOException {

        Files.createDirectories(storagePath);

        Path tempDir = storagePath.resolve(fileName + "_chunks");
        Files.createDirectories(tempDir);

        Path chunkFile = tempDir.resolve("chunk_" + chunkNumber);
        Files.write(chunkFile, chunk.getBytes());

        return ResponseEntity.ok("Chunk " + chunkNumber + " uploaded.");
    }

    @PostMapping("/merge")
    public ResponseEntity<String> mergeChunks(
            @RequestParam("fileName") String fileName,
            @RequestParam("totalChunks") int totalChunks
    ) throws IOException {

        Path tempDir = storagePath.resolve(fileName + "_chunks");
        Path finalFile = storagePath.resolve(fileName);

        try (OutputStream os = Files.newOutputStream(finalFile)) {
            for (int i = 1; i <= totalChunks; i++) {
                Path chunk = tempDir.resolve("chunk_" + i);
                Files.copy(chunk, os);
            }
        }

        // Optional: Delete chunks after merge
        FileSystemUtils.deleteRecursively(tempDir);

        return ResponseEntity.ok("File uploaded successfully!");
    }
}