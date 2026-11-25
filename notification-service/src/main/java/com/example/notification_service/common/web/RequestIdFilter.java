package com.example.notification_service.common.web;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
 public static final String HEADER="X-Request-Id",MDC_KEY="requestId",ATTRIBUTE=RequestIdFilter.class.getName()+".requestId";
 private static final Pattern SAFE=Pattern.compile("[A-Za-z0-9._-]{1,128}");
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
  String id=request.getHeader(HEADER);
  if(id==null||!SAFE.matcher(id).matches()){chain.doFilter(request,response);return;}
  request.setAttribute(ATTRIBUTE,id);response.setHeader(HEADER,id);MDC.put(MDC_KEY,id);
  try{chain.doFilter(request,response);}finally{MDC.remove(MDC_KEY);}
 }
}
