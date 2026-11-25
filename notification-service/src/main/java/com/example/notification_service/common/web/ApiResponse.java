package com.example.notification_service.common.web;
import com.fasterxml.jackson.annotation.JsonInclude;
public record ApiResponse<T>(T data,@JsonInclude(JsonInclude.Include.NON_NULL) Object meta){
 public static <T> ApiResponse<T> of(T data){return new ApiResponse<>(data,null);}
}
