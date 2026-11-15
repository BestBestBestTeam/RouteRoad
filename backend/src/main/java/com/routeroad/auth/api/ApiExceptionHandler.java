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
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> internal(Exception exception) {
        // Exception text may include request values or SQL parameters; never serialize it.
        return ResponseEntity.internalServerError().body(ApiError.of("INTERNAL_ERROR", "Внутренняя ошибка сервера"));
    }
}
