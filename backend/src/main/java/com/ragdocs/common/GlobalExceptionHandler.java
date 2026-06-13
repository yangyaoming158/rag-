package com.ragdocs.common;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(httpStatus(ex.errorCode())).body(ApiResponse.error(ex.errorCode(), ex.getMessage()));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.PARAM_ERROR, firstMessage(ex)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage()));
    }

    private HttpStatus httpStatus(ErrorCode errorCode) {
        return switch (errorCode) {
            case PARAM_ERROR -> HttpStatus.BAD_REQUEST;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNSUPPORTED_FILE -> HttpStatus.UNPROCESSABLE_ENTITY;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
            case LLM_CALL_FAILED, EMBEDDING_CALL_FAILED -> HttpStatus.BAD_GATEWAY;
        };
    }

    private String firstMessage(Exception ex) {
        if (ex instanceof MethodArgumentNotValidException validationException
                && validationException.getBindingResult().hasErrors()) {
            return validationException.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        }
        if (ex instanceof BindException bindException && bindException.getBindingResult().hasErrors()) {
            return bindException.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        }
        return ex.getMessage() == null ? ErrorCode.PARAM_ERROR.defaultMessage() : ex.getMessage();
    }
}
