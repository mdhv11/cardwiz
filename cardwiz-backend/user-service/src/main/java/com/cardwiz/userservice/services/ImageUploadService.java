package com.cardwiz.userservice.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

/**
 * Service for handling image uploads to S3
 * Handles image validation, resizing, compression, and S3 upload
 * Uses AWS SDK V2
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImageUploadService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket-name:epoch-profile-images}")
    private String bucketName;

    @Value("${aws.s3.enabled:false}")
    private boolean s3Enabled;

    // Image constraints
    private static final long MAX_FILE_SIZE = 2 * 1024 * 1024; // 2MB
    private static final long MAX_DOCUMENT_SIZE = 20 * 1024 * 1024; // 20MB
    private static final int MAX_WIDTH = 400;
    private static final int MAX_HEIGHT = 400;
    private static final String[] ALLOWED_EXTENSIONS = {"jpg", "jpeg", "png", "gif", "webp"};
    private static final String[] ALLOWED_DOCUMENT_EXTENSIONS = {"jpg", "jpeg", "png", "pdf", "webp"};

    /**
     * Upload user profile image to S3
     *
     * @param file Image file from user
     * @param userId User ID for organizing in S3
     * @return S3 object key of uploaded image
     */
    public String uploadProfileImage(MultipartFile file, Long userId) {
        if (!s3Enabled) {
            throw new RuntimeException("S3 is not enabled.");
        }
        validateImage(file);

        try {
            byte[] processedImage = processImage(file);

            // 3. Generate unique filename
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null) {
                originalFilename = "image.jpg";
            }
            String filename = generateFilename(userId, originalFilename);

            // 4. Upload to S3
            uploadToS3(processedImage, filename, file.getContentType());
            return filename;

        } catch (IOException e) {
            log.error("Error processing image for user {}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to process image");
        } catch (Exception e) {
            log.error("Error uploading image to S3 for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to upload image to S3: " + e.getMessage());
        }
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
                    .bucket(bucketName)
                    .key(key)
                    .contentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(file.getBytes()));
            return key;
        } catch (IOException e) {
            log.error("Error reading document for user {}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to process document");
        } catch (Exception e) {
            log.error("Error uploading document to S3 for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to upload document to S3: " + e.getMessage());
        }
    }

    public String getBucketName() {
        return bucketName;
    }

    /**
     * Validate image file
     */
    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        // Check file size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                String.format("File size exceeds maximum of %d MB", MAX_FILE_SIZE / (1024 * 1024))
            );
        }

        // Check file extension
        String filename = file.getOriginalFilename();
        if (filename == null || !isAllowedExtension(filename)) {
            throw new IllegalArgumentException(
                "Invalid file type. Allowed types: jpg, jpeg, png, gif, webp"
            );
        }

        // Check MIME type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("File must be an image");
        }
    }

    /**
     * Check if file extension is allowed
     */
    private boolean isAllowedExtension(String filename) {
        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        for (String allowed : ALLOWED_EXTENSIONS) {
            if (extension.equals(allowed)) {
                return true;
            }
        }
        return false;
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

    /**
     * Process image: resize and compress
     */
    private byte[] processImage(MultipartFile file) throws IOException {
        // Read original image
        BufferedImage originalImage = ImageIO.read(file.getInputStream());
        if (originalImage == null) {
            throw new IOException("Failed to read image file");
        }

        // Resize if necessary
        BufferedImage resizedImage = resizeImage(originalImage);

        // Compress and return
        return compressImage(resizedImage);
    }

    /**
     * Resize image to max dimensions while maintaining aspect ratio
     */
    private BufferedImage resizeImage(BufferedImage originalImage) {
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();

        // If image is already smaller than max, return as-is
        if (originalWidth <= MAX_WIDTH && originalHeight <= MAX_HEIGHT) {
            return originalImage;
        }

        // Calculate new dimensions maintaining aspect ratio
        int newWidth = originalWidth;
        int newHeight = originalHeight;

        if (originalWidth > MAX_WIDTH) {
            newWidth = MAX_WIDTH;
            newHeight = (originalHeight * MAX_WIDTH) / originalWidth;
        }

        if (newHeight > MAX_HEIGHT) {
            newHeight = MAX_HEIGHT;
            newWidth = (newWidth * MAX_HEIGHT) / newHeight;
        }

        // Create resized image
        BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        resizedImage.getGraphics().drawImage(originalImage, 0, 0, newWidth, newHeight, null);

        return resizedImage;
    }

    /**
     * Compress image to JPEG
     */
    private byte[] compressImage(BufferedImage image) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // Write as JPEG with compression
        boolean written = ImageIO.write(image, "jpg", outputStream);

        if (!written) {
            throw new IOException("Failed to compress image");
        }

        return outputStream.toByteArray();
    }

    /**
     * Generate unique filename for S3
     */
    private String generateFilename(Long userId, String originalFilename) {
        String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        return String.format("profile-images/%d/%s%s", userId, UUID.randomUUID(), extension);
    }

    private String generateDocumentFilename(Long userId, String originalFilename) {
        String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        return String.format("documents/%d/%s%s", userId, UUID.randomUUID(), extension);
    }

    /**
     * Upload to S3
     */
    private void uploadToS3(byte[] imageData, String filename, String contentType) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(filename)
                .contentType(contentType != null ? contentType : "image/jpeg")
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromBytes(imageData));
    }

    public String getImageUrl(String s3Key) {
        if (s3Key == null || s3Key.isBlank()) {
            return null;
        }
        if (!s3Enabled) {
            return s3Key;
        }
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(60))
                    .getObjectRequest(getObjectRequest)
                    .build();

            return s3Presigner.presignGetObject(presignRequest).url().toString();
        } catch (Exception e) {
            log.error("Failed to generate presigned URL for {}: {}", s3Key, e.getMessage());
            return null;
        }
    }

    /**
     * Delete image from S3
     */
    public void deleteProfileImage(String imageKey) {
        if (!s3Enabled || imageKey == null || imageKey.isBlank()) {
            return;
        }

        try {
            s3Client.deleteObject(builder -> builder.bucket(bucketName).key(imageKey));
            log.info("Image deleted from S3: {}", imageKey);
        } catch (Exception e) {
            log.error("Error deleting image from S3: {}", imageKey, e);
        }
    }
}
