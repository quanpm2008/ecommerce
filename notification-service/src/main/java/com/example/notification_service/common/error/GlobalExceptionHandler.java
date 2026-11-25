package com.example.notification_service.common.error;
import com.example.notification_service.common.web.RequestIdFilter;
import java.util.List;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
 @ExceptionHandler(ApiException.class)
 ResponseEntity<Object> api(ApiException ex,WebRequest request){return error(ex.code(),ex.details(),ex.code().status(),new HttpHeaders(),request);}
 @ExceptionHandler(MethodArgumentTypeMismatchException.class)
 ResponseEntity<Object> invalid(MethodArgumentTypeMismatchException ex,WebRequest request){return api(ApiException.forField(ErrorCode.VALIDATION_ERROR,ex.getName(),"INVALID_FORMAT"),request);}
 @ExceptionHandler(Exception.class)
 ResponseEntity<Object> unexpected(Exception ex,WebRequest request){
  // Do not log exception messages or request bodies: an invalid event can contain a secret.
  logger.error("Notification request failed: "+ex.getClass().getSimpleName());
  return error(ErrorCode.INTERNAL_ERROR,List.of(),HttpStatus.INTERNAL_SERVER_ERROR,new HttpHeaders(),request);
 }
 @Override
 protected ResponseEntity<Object> handleExceptionInternal(Exception ex,Object body,HttpHeaders headers,HttpStatusCode status,WebRequest request){
  ErrorCode code=switch(status.value()){case 401->ErrorCode.UNAUTHENTICATED;case 403->ErrorCode.FORBIDDEN;case 404->ErrorCode.NOT_FOUND;case 503->ErrorCode.SERVICE_UNAVAILABLE;default->status.is5xxServerError()?ErrorCode.INTERNAL_ERROR:ErrorCode.VALIDATION_ERROR;};
  return error(code,List.of(),status,headers,request);
 }
 private ResponseEntity<Object> error(ErrorCode code,List<Map<String,Object>> details,HttpStatusCode status,HttpHeaders headers,WebRequest request){
  Object id=request.getAttribute(RequestIdFilter.ATTRIBUTE,RequestAttributes.SCOPE_REQUEST);
  return ResponseEntity.status(status).headers(headers).body(new Envelope(new Body(code.name(),code.defaultMessage(),details,id==null?null:id.toString())));
 }
 record Envelope(Body error){}
 record Body(String code,String message,List<Map<String,Object>> details,String requestId){}
}
