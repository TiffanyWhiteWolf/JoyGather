package com.quju.controller;

import com.quju.common.ApiResponse;
import com.quju.dto.CommonDtos;
import com.quju.dto.UserDto;
import com.quju.service.FileStorageService;
import com.quju.service.UserService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileInputStream;

@RestController
@RequestMapping("/files")
public class FileController {
    private final FileStorageService fileStorageService;
    private final UserService userService;

    public FileController(FileStorageService fileStorageService, UserService userService) {
        this.fileStorageService = fileStorageService;
        this.userService = userService;
    }

    @PostMapping("/upload")
    public ApiResponse<CommonDtos.FileResponse> upload(@RequestParam("file") MultipartFile file,
                                                       @RequestHeader(value = "Authorization", required = false) String authorization) {
        UserDto user = userService.requireToken(authorization);
        return ApiResponse.success(fileStorageService.upload(file, user.getId()));
    }

    @PostMapping("/avatar")
    public ApiResponse<CommonDtos.FileResponse> avatar(@RequestParam("file") MultipartFile file,
                                                       @RequestHeader(value = "Authorization", required = false) String authorization) {
        UserDto user = userService.requireToken(authorization);
        return ApiResponse.success(fileStorageService.uploadAvatar(file, user.getId()));
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<InputStreamResource> content(@PathVariable String id) throws Exception {
        FileStorageService.LocalFile file = fileStorageService.localFile(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.originalName + "\"")
                .contentType(MediaType.parseMediaType(file.contentType == null ? "application/octet-stream" : file.contentType))
                .body(new InputStreamResource(new FileInputStream(file.path.toFile())));
    }
}
