package com.example.notification_service.common.error;
import java.util.List;
import java.util.Map;
public class ApiException extends RuntimeException {
 private final ErrorCode code;
 private final List<Map<String,Object>> details;
 public ApiException(ErrorCode code){this(code,List.of());}
 public ApiException(ErrorCode code,List<Map<String,Object>> details){super(code.defaultMessage());this.code=code;this.details=List.copyOf(details);}
 public static ApiException forField(ErrorCode code,String field,String issue){return new ApiException(code,List.of(Map.of("field",field,"issue",issue,"message",code.defaultMessage())));}
 public ErrorCode code(){return code;}
 public List<Map<String,Object>> details(){return details;}
}
