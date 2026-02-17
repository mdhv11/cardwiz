package com.cardwiz.userservice.services;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.util.UUID;

/**
 * Service for handling document uploads to S3
 * Uses AWS SDK V2
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImageUploadService {

    private final S3Client s3Client;

    @Value("${aws.s3.document-bucket-name:epoch-docs}")
    private String documentBucketName;

    @Value("${aws.s3.enabled:true}")
    private boolean s3Enabled;

    private static final long MAX_DOCUMENT_SIZE = 20 * 1024 * 1024; // 20MB
    private static final String[] ALLOWED_DOCUMENT_EXTENSIONS = {"jpg", "jpeg", "png", "pdf", "webp"};

    @PostConstruct
    public void initializeBuckets() {
        if (!s3Enabled) {
            return;
        }
        ensureBucketExists(documentBucketName);
    }

    public String uploadDocument(MultipartFile file, Long userId) {
        if (!s3Enabled) {
            throw new RuntimeException("S3 is not enabled.");
        }
        validateDocument(file);

        try {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null) {
                originalFilename = "document";
            }
            String key = generateDocumentFilename(userId, originalFilename);

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(documentBucketName)
                    .key(key)
                    .contentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                    .build();

            putObjectWithBucketAutoCreate(documentBucketName, putObjectRequest, RequestBody.fromBytes(file.getBytes()));
            return key;
        } catch (IOException e) {
            log.error("Error reading document for user {}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to process document");
        } catch (Exception e) {
            log.error("Error uploading document to S3 for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to upload document to S3: " + e.getMessage());
        }
    }


    public String getDocumentBucketName() {
        return documentBucketName;
    }

    private boolean isAllowedDocumentExtension(String filename) {
        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        for (String allowed : ALLOWED_DOCUMENT_EXTENSIONS) {
            if (extension.equals(allowed)) {
                return true;
            }
        }
        return false;
    }

    private void validateDocument(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (file.getSize() > MAX_DOCUMENT_SIZE) {
            throw new IllegalArgumentException(
                    String.format("Document size exceeds maximum of %d MB", MAX_DOCUMENT_SIZE / (1024 * 1024))
            );
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !isAllowedDocumentExtension(filename)) {
            throw new IllegalArgumentException("Invalid document type. Allowed: jpg, jpeg, png, webp, pdf");
        }
    }

    private String generateDocumentFilename(Long userId, String originalFilename) {
        String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        return String.format("documents/%d/%s%s", userId, UUID.randomUUID(), extension);
    }

    private void putObjectWithBucketAutoCreate(String bucketName, PutObjectRequest request, RequestBody body) {
        try {
            s3Client.putObject(request, body);
        } catch (S3Exception ex) {
            if (isNoSuchBucket(ex)) {
                log.warn("Bucket {} not found. Creating bucket and retrying upload once.", bucketName);
                ensureBucketExists(bucketName);
                s3Client.putObject(request, body);
                return;
            }
            throw ex;
        }
    }

    private void ensureBucketExists(String bucketName) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
        } catch (S3Exception ex) {
            String errorCode = ex.awsErrorDetails() != null ? ex.awsErrorDetails().errorCode() : null;
            if (ex.statusCode() == 404 || "NoSuchBucket".equals(errorCode)) {
                log.info("Creating missing S3 bucket: {}", bucketName);
                s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
                return;
            }
            throw ex;
        }
    }

    private boolean isNoSuchBucket(S3Exception ex) {
        if (ex.statusCode() == 404) {
            return true;
        }
        return ex.awsErrorDetails() != null && "NoSuchBucket".equals(ex.awsErrorDetails().errorCode());
    }

}
