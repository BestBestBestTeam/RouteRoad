package com.routeroad.auth.api;

import com.routeroad.auth.application.AccountException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(AccountException.class)
    ResponseEntity<ApiError> account(AccountException exception) {
        var status = switch (exception.kind()) {
            case INVALID -> 400; case UNAUTHENTICATED -> 401; case CONFLICT, SCENARIO_RESET -> 409; case NOT_FOUND -> 404;
        };
        var code = switch (exception.kind()) {
            case INVALID -> "INVALID_REQUEST"; case UNAUTHENTICATED -> "UNAUTHENTICATED";
            case CONFLICT -> "REGISTRATION_CONFLICT"; case SCENARIO_RESET -> "SCENARIO_RESET"; case NOT_FOUND -> "NOT_FOUND";
        };
        return ResponseEntity.status(status).body(ApiError.of(code, exception.getMessage()));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class,
            MissingRequestHeaderException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> invalid(Exception exception) {
        return ResponseEntity.badRequest().body(ApiError.of("INVALID_REQUEST", "Некорректный запрос"));
    }
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> unauthenticated(AuthenticationException exception) {
        return ResponseEntity.status(401).body(ApiError.of("INVALID_CREDENTIALS", "Неверный логин или пароль"));
    }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> forbidden(AccessDeniedException exception) {
        return ResponseEntity.status(403).body(ApiError.of("FORBIDDEN", "Недостаточно прав"));
    }
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> methodNotAllowed(org.springframework.web.HttpRequestMethodNotSupportedException exception) {
        return ResponseEntity.status(exception.getStatusCode()).headers(exception.getHeaders())
                .body(ApiError.of("METHOD_NOT_ALLOWED", "Метод не поддерживается"));
    }
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    ResponseEntity<ApiError> resourceNotFound(org.springframework.web.servlet.resource.NoResourceFoundException exception) {
        return ResponseEntity.status(exception.getStatusCode()).headers(exception.getHeaders())
                .body(ApiError.of("NOT_FOUND", "Ресурс не найден"));
    }
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> unsupportedMediaType(org.springframework.web.HttpMediaTypeNotSupportedException exception) {
        return ResponseEntity.status(exception.getStatusCode()).headers(exception.getHeaders())
                .body(ApiError.of("UNSUPPORTED_MEDIA_TYPE", "Неподдерживаемый тип содержимого"));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> internal(Exception exception) {
        // Exception text may include request values or SQL parameters; never serialize it.
        return ResponseEntity.internalServerError().body(ApiError.of("INTERNAL_ERROR", "Внутренняя ошибка сервера"));
    }
}
