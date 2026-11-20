package com.example.product_service.client.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateNotificationInternalRequest {

    private String eventId;

    private UUID recipientUserId;

    private String audienceContext;

    private String type;

    private List<String> channels;

    private Map<String, Object> reference;

    private Map<String, Object> templateData;

}
