package cn.sduonline.invoice.storage;

import cn.sduonline.invoice.data.enums.BizCode;
import cn.sduonline.invoice.exception.BusinessException;
import org.springframework.http.HttpStatus;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.io.IOException;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class R2ObjectStorage implements ObjectStorage, AutoCloseable {
    private final R2StorageProperties properties;
    private final S3Client client;
    private final S3Presigner presigner;

    R2ObjectStorage(R2StorageProperties properties) {
        this.properties = properties;
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(
                properties.getAccessKeyId(), properties.getSecretAccessKey()));
        URI endpoint = URI.create("https://" + properties.getAccountId()
                + ".r2.cloudflarestorage.com");
        S3Configuration serviceConfiguration = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .chunkedEncodingEnabled(false)
                .build();
        this.client = S3Client.builder()
                .endpointOverride(endpoint)
                .credentialsProvider(credentials)
                .region(Region.of("auto"))
                .serviceConfiguration(serviceConfiguration)
                .build();
        this.presigner = S3Presigner.builder()
                .endpointOverride(endpoint)
                .credentialsProvider(credentials)
                .region(Region.of("auto"))
                .serviceConfiguration(serviceConfiguration)
                .build();
    }

    @Override
    public UploadGrant createUploadGrant(String key, String contentType, long sizeBytes, String sha256) {
        try {
            PutObjectRequest objectRequest = PutObjectRequest.builder()
                    .bucket(properties.getBucket()).key(uploadKey(key)).contentType(contentType)
                    .contentLength(sizeBytes)
                    .metadata(Map.of("sha256", sha256.toLowerCase(Locale.ROOT)))
                    .build();
            PresignedPutObjectRequest signed = presigner.presignPutObject(
                    PutObjectPresignRequest.builder()
                            .signatureDuration(properties.getUploadUrlTtl())
                            .putObjectRequest(objectRequest).build());
            return new UploadGrant(signed.url().toString(), signed.httpRequest().headers(),
                    Instant.now().plus(properties.getUploadUrlTtl()));
        } catch (SdkException exception) {
            throw unavailable();
        }
    }

    @Override
    public Optional<StoredObject> headUpload(String key) {
        Optional<StoredObject> pending = headObject(uploadKey(key));
        return pending.isPresent() ? pending : headObject(key);
    }

    @Override
    public void finalizeUpload(String key) {
        String pendingKey = uploadKey(key);
        try {
            if (headObject(pendingKey).isEmpty()) {
                if (headObject(key).isPresent()) return;
                throw new BusinessException(BizCode.FILE_UPLOAD_INVALID, HttpStatus.CONFLICT);
            }
            String encodedSource = properties.getBucket() + "/"
                    + URLEncoder.encode(pendingKey, StandardCharsets.UTF_8).replace("+", "%20");
            client.copyObject(CopyObjectRequest.builder()
                    .bucket(properties.getBucket()).key(key).copySource(encodedSource).build());
            client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.getBucket()).key(pendingKey).build());
        } catch (BusinessException exception) {
            throw exception;
        } catch (SdkException exception) {
            throw unavailable();
        }
    }

    private Optional<StoredObject> headObject(String key) {
        try {
            HeadObjectResponse response = client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.getBucket()).key(key).build());
            return Optional.of(new StoredObject(response.contentLength(), response.contentType(),
                    response.metadata()));
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) return Optional.empty();
            throw unavailable();
        } catch (SdkException exception) {
            throw unavailable();
        }
    }

    private String uploadKey(String key) {
        return key + ".upload";
    }

    @Override
    public void downloadTo(String key, Path target) {
        try (var output = Files.newOutputStream(target, StandardOpenOption.TRUNCATE_EXISTING)) {
            client.getObject(GetObjectRequest.builder()
                            .bucket(properties.getBucket()).key(key).build(),
                    ResponseTransformer.toOutputStream(output));
        } catch (SdkException | IOException exception) {
            throw unavailable();
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.getBucket()).key(key).build());
            client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.getBucket()).key(uploadKey(key)).build());
        } catch (SdkException exception) {
            throw unavailable();
        }
    }

    @Override
    public DownloadGrant createDownloadGrant(String key, String originalName, String contentType) {
        try {
            String encoded = URLEncoder.encode(originalName, StandardCharsets.UTF_8)
                    .replace("+", "%20");
            GetObjectRequest objectRequest = GetObjectRequest.builder()
                    .bucket(properties.getBucket()).key(key)
                    .responseContentType(contentType)
                    .responseContentDisposition("attachment; filename=\"download\"; filename*=UTF-8''" + encoded)
                    .build();
            PresignedGetObjectRequest signed = presigner.presignGetObject(
                    GetObjectPresignRequest.builder()
                            .signatureDuration(properties.getDownloadUrlTtl())
                            .getObjectRequest(objectRequest).build());
            return new DownloadGrant(signed.url().toString(),
                    Instant.now().plus(properties.getDownloadUrlTtl()));
        } catch (SdkException exception) {
            throw unavailable();
        }
    }

    private BusinessException unavailable() {
        return new BusinessException(BizCode.THIRD_PARTY_UNAVAILABLE,
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Override
    public void close() {
        presigner.close();
        client.close();
    }
}
