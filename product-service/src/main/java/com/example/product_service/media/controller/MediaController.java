package com.example.product_service.media.controller;

import com.example.product_service.media.common.ApiResponse;
import com.example.product_service.media.dto.request.UploadUrlItemRequest;
import com.example.product_service.media.dto.response.UploadUrlResponse;
import com.example.product_service.media.service.MediaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @PostMapping("/upload-urls")
    public ResponseEntity<ApiResponse<List<UploadUrlResponse>>> getUploadUrls(
            @RequestBody @NotEmpty List<@Valid UploadUrlItemRequest> files
    ) {
        List<UploadUrlResponse> responses = mediaService.generateUploadUrls(files);
        return ResponseEntity.ok(ApiResponse.of(responses));
    }

}
