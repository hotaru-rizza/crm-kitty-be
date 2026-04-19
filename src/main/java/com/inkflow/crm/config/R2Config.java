package com.inkflow.crm.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class R2Config {

    private final R2Properties r2Properties;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create("https://" + r2Properties.getAccountId() + ".r2.cloudflarestorage.com"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                r2Properties.getAccessKeyId(),
                                r2Properties.getSecretAccessKey()
                        )
                ))
                .region(Region.US_EAST_1) // required by SDK, R2 ignores it
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .endpointOverride(URI.create("https://" + r2Properties.getAccountId() + ".r2.cloudflarestorage.com"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                r2Properties.getAccessKeyId(),
                                r2Properties.getSecretAccessKey()
                        )
                ))
                .region(Region.US_EAST_1)
                .build();
    }

    @PostConstruct
    public void verifyR2Connection() {
        if (!r2Properties.isVerifyConnection()) {
            log.warn("R2 verifyConnection вимкнено — перевірку bucket пропущено");
            return;
        }
        try (S3Client client = S3Client.builder()
                .endpointOverride(URI.create("https://" + r2Properties.getAccountId() + ".r2.cloudflarestorage.com"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(r2Properties.getAccessKeyId(), r2Properties.getSecretAccessKey())
                ))
                .region(Region.US_EAST_1)
                .build()) {
            client.headBucket(HeadBucketRequest.builder()
                    .bucket(r2Properties.getBucketName())
                    .build());
            log.info("✅ R2 connection OK — bucket '{}' is accessible", r2Properties.getBucketName());
        } catch (Exception e) {
            log.error("❌ R2 connection FAILED — bucket '{}': {}", r2Properties.getBucketName(), e.getMessage());
            throw new IllegalStateException("R2 storage is not accessible. Check your R2_* environment variables.", e);
        }
    }
}
