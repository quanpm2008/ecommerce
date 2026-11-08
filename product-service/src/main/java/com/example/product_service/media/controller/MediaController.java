package com.example.product_service.media.controller;

import com.example.product_service.common.response.ApiResponse;
import com.example.product_service.media.dto.request.UploadUrlItemRequest;
import com.example.product_service.media.dto.response.UploadUrlResponse;
import com.example.product_service.media.service.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "8. Media", description = "Xin presigned URL upload hình ảnh/video sản phẩm và review")
@Validated
@RestController
@RequestMapping("/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @Operation(summary = "Tạo URL upload media (ảnh/video) kèm hạn sử dụng")
    @PostMapping("/upload-urls")
    public ResponseEntity<ApiResponse<List<UploadUrlResponse>>> getUploadUrls(
            @RequestBody @NotEmpty List<@Valid UploadUrlItemRequest> files
    ) {
        List<UploadUrlResponse> responses = mediaService.generateUploadUrls(files);
        return ResponseEntity.ok(ApiResponse.of(responses));
    }

}
