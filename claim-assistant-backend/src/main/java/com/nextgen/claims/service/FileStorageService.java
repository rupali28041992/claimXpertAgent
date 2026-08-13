package com.nextgen.claims.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Stores the raw uploaded file and returns a reference. Local disk for the prototype; swap for S3/blob storage later. */
@Service
public class FileStorageService {

    @Value("${claims.upload.dir:./uploads}")
    private String uploadDir;

    public String store(MultipartFile file) {
        try {
            Path dir = Path.of(uploadDir);
            Files.createDirectories(dir);
            String fileName = UUID.randomUUID() + "-" + file.getOriginalFilename();
            Path target = dir.resolve(fileName);
            file.transferTo(target);
            return target.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
