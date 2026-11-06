package com.example.product_service.media.common.exception;

import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Getter
public class MediaInvalidException extends RuntimeException {

    private final List<Map<String, Object>> details;

    public MediaInvalidException(String message) {
        super(message);
        this.details = Collections.emptyList();
    }

    public MediaInvalidException(String message, List<Map<String, Object>> details) {
        super(message);
        this.details = details != null ? details : Collections.emptyList();
    }
}
