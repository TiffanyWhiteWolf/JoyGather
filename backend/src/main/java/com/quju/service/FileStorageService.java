package com.quju.service;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.quju.dto.CommonDtos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class FileStorageService {
    private final JdbcTemplate jdbc;
    private final IntegrationService integrationService;

    @Value("${quju.app.uploads-dir:../.build/uploads}")
    private String uploadsDir;
    @Value("${quju.s3.endpoint:}")
    private String s3Endpoint;
    @Value("${quju.s3.region:us-east-1}")
    private String s3Region;
    @Value("${quju.s3.bucket:}")
    private String s3Bucket;
    @Value("${quju.s3.access-key:}")
    private String s3AccessKey;
    @Value("${quju.s3.secret-key:}")
    private String s3SecretKey;
    @Value("${quju.s3.public-base-url:}")
    private String s3PublicBaseUrl;

    public FileStorageService(JdbcTemplate jdbc, IntegrationService integrationService) {
        this.jdbc = jdbc;
        this.integrationService = integrationService;
    }

    public CommonDtos.FileResponse upload(MultipartFile file, String ownerId) {
        if (file == null || file.isEmpty()) throw new IllegalStateException("请选择要上传的文件");
        if (file.getSize() > 10 * 1024 * 1024) throw new IllegalStateException("单个文件不能超过 10MB");
        String id = DbSupport.id("file");
        String original = DbSupport.safe(file.getOriginalFilename(), "upload.bin");
        String extension = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0 && dot < original.length() - 1) extension = original.substring(dot);
        String key = ownerId + "/" + id + extension;
        String provider = s3Configured() ? "S3" : "LOCAL";
        String url;
        try {
            if (s3Configured()) {
                uploadS3(file, key);
                url = publicS3Url(key);
            } else {
                Path target = Paths.get(uploadsDir).toAbsolutePath().normalize().resolve(key).normalize();
                Files.createDirectories(target.getParent());
                file.transferTo(target.toFile());
                url = "/api/files/" + id + "/content";
                integrationService.logThirdParty("S3", "UPLOAD", "DEGRADED", original, url, "S3 未配置，使用项目本地 .build/uploads", 0);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("文件上传失败：" + ex.getMessage());
        }
        jdbc.update("insert into files (id,owner_id,original_name,content_type,size_bytes,url,storage_key,provider) values (?,?,?,?,?,?,?,?)",
                id, ownerId, original, file.getContentType(), file.getSize(), url, key, provider);
        return fileResponse(id);
    }

    public CommonDtos.FileResponse fileResponse(String id) {
        return jdbc.queryForObject("select * from files where id = ?", (rs, rowNum) -> {
            CommonDtos.FileResponse response = new CommonDtos.FileResponse();
            response.setId(rs.getString("id"));
            response.setUrl(rs.getString("url"));
            response.setOriginalName(rs.getString("original_name"));
            response.setContentType(rs.getString("content_type"));
            response.setSize(rs.getLong("size_bytes"));
            response.setProvider(rs.getString("provider"));
            return response;
        }, id);
    }

    public LocalFile localFile(String id) {
        return jdbc.queryForObject("select * from files where id = ?", (rs, rowNum) -> {
            if (!"LOCAL".equals(rs.getString("provider"))) throw new IllegalStateException("该文件不在本地存储");
            LocalFile file = new LocalFile();
            file.path = Paths.get(uploadsDir).toAbsolutePath().normalize().resolve(rs.getString("storage_key")).normalize();
            file.contentType = rs.getString("content_type");
            file.originalName = rs.getString("original_name");
            return file;
        }, id);
    }

    private void uploadS3(MultipartFile file, String key) throws Exception {
        AmazonS3 s3 = AmazonS3ClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(s3Endpoint, s3Region))
                .withPathStyleAccessEnabled(true)
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(s3AccessKey, s3SecretKey)))
                .build();
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentType(file.getContentType());
        try (InputStream input = file.getInputStream()) {
            s3.putObject(s3Bucket, key, input, metadata);
        }
        integrationService.logThirdParty("S3", "UPLOAD", "SUCCESS", file.getOriginalFilename(), key, "", 0);
    }

    private String publicS3Url(String key) {
        if (s3PublicBaseUrl != null && !s3PublicBaseUrl.trim().isEmpty()) return trimSlash(s3PublicBaseUrl) + "/" + key;
        return trimSlash(s3Endpoint) + "/" + s3Bucket + "/" + key;
    }

    private boolean s3Configured() {
        return notBlank(s3Endpoint) && notBlank(s3Bucket) && notBlank(s3AccessKey) && notBlank(s3SecretKey);
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String trimSlash(String value) {
        if (value == null) return "";
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public static class LocalFile {
        public Path path;
        public String contentType;
        public String originalName;
    }
}
