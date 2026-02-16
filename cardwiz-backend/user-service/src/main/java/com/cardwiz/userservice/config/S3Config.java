package com.cardwiz.userservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * AWS S3 Configuration for file uploads
 * Uses AWS SDK V2 (latest version)
 * Supports both IAM Role (production) and explicit credentials (development)
 */
@Configuration
@RequiredArgsConstructor
public class S3Config {

    // Optional credentials - if not provided, will use IAM role
    @Value("${aws.s3.access-key:}")
    private String awsAccessKey;

    @Value("${aws.s3.secret-key:}")
    private String awsSecretKey;

    @Value("${aws.s3.region:ap-south-1}")
    private String awsRegion;

    // Optional endpoint for local development (MinIO/LocalStack)
    @Value("${aws.s3.endpoint:}")
    private String awsEndpoint;

    @Value("${aws.s3.enabled:true}")
    private boolean s3Enabled;

    /**
     * Create S3Client bean
     * Priority:
     * 1. Explicit credentials (if provided in properties)
     * 2. IAM Role (EC2 instance profile)
     * 3. Environment variables (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)
     * 4. AWS credentials file (~/.aws/credentials)
     */
    @Bean
    public S3Client s3Client() {
        if (!s3Enabled) {
            throw new IllegalStateException("S3 is disabled but S3Client bean was requested");
        }

        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(awsRegion));

        // Handle custom endpoint (for local development only)
        if (awsEndpoint != null && !awsEndpoint.isBlank()) {
            builder.endpointOverride(URI.create(awsEndpoint))
                    .forcePathStyle(true);
        }

        // Handle credentials
        if (hasExplicitCredentials()) {
            // Use explicit credentials (development/testing)
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(awsAccessKey, awsSecretKey)
            ));
        } else {
            // Use default credential provider chain (production with IAM role)
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        if (!s3Enabled) {
            throw new IllegalStateException("S3 is disabled but S3Presigner bean was requested");
        }

        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(awsRegion));

        // Handle custom endpoint (for local development only)
        if (awsEndpoint != null && !awsEndpoint.isBlank()) {
            builder.endpointOverride(URI.create(awsEndpoint));
        }

        // Handle credentials
        if (hasExplicitCredentials()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(awsAccessKey, awsSecretKey)
            ));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        return builder.build();
    }

    /**
     * Check if explicit AWS credentials are provided
     */
    private boolean hasExplicitCredentials() {
        return awsAccessKey != null && !awsAccessKey.isBlank()
                && awsSecretKey != null && !awsSecretKey.isBlank();
    }
}