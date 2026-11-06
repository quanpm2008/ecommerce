package com.example.product_service.category.utils.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiPageResponse<T> {

    private List<T> data;

    private PageMetaResponse meta;
}
