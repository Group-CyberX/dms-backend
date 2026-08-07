package com.dms.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class S3Config {

    @Value("${app.storage.type:local}")
    private String storageType;

    @Value("${app.s3.region:ap-south-1}")
    private String region;

    @Value("${app.s3.access-key:}")
    private String accessKey;

    @Value("${app.s3.secret-key:}")
    private String secretKey;

    private AwsCredentialsProvider buildCredentialsProvider() {
        if (accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank()) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        }
        return DefaultCredentialsProvider.create();
    }

    @Bean
    public S3Client s3Client() {
        // Build credentials provider: use static if keys provided, otherwise default provider chain (env/instance-profile)
        AwsCredentialsProvider provider = buildCredentialsProvider();
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(provider)
                .build();
    }

    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner() {
        // Use the same region and credentials as S3Client
        AwsCredentialsProvider provider = buildCredentialsProvider();
        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(provider)
                .build();
    }
}
