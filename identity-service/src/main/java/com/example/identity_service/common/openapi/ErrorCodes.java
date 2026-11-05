package com.example.identity_service.common.openapi;

import com.example.identity_service.common.error.ErrorCode;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The business errors an endpoint can return, for the API docs only. {@link OpenApiConfig} turns them into responses
 * grouped by HTTP status, with each code's message from {@link ErrorCode}. Validation (400) and authentication (401,
 * 403) errors are added automatically and need not be listed.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ErrorCodes {

	ErrorCode[] value();

}
