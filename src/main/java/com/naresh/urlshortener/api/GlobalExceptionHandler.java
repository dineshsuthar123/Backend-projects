package com.naresh.urlshortener.api;

import com.naresh.urlshortener.service.AliasAlreadyExistsException;
import com.naresh.urlshortener.service.UrlExpiredException;
import com.naresh.urlshortener.service.UrlNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    ResponseEntity<ErrorResponse> status(org.springframework.web.server.ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(ErrorResponse.of(ex.getStatusCode().value(),
                "REQUEST_REJECTED", ex.getReason()));
    }

    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorResponse> malformed(Exception ex) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(400, "INVALID_REQUEST", "Malformed request"));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponse> conflict(Exception ex) {
        return ResponseEntity.status(409).body(ErrorResponse.of(409, "CONFLICT", "The requested alias is unavailable"));
    }

    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    ResponseEntity<ErrorResponse> databaseUnavailable(Exception ex) {
        return ResponseEntity.status(503).header("Retry-After", "5")
                .body(ErrorResponse.of(503, "STORAGE_UNAVAILABLE", "Persistent storage is temporarily unavailable"));
    }

    @ExceptionHandler(UrlNotFoundException.class)
    ResponseEntity<ErrorResponse> notFound(UrlNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, "SHORT_CODE_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(UrlExpiredException.class)
    ResponseEntity<ErrorResponse> expired(UrlExpiredException ex) {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(ErrorResponse.of(410, "SHORT_URL_EXPIRED", ex.getMessage()));
    }

    @ExceptionHandler(AliasAlreadyExistsException.class)
    ResponseEntity<ErrorResponse> aliasConflict(AliasAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, "CUSTOM_ALIAS_ALREADY_EXISTS", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::format)
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "VALIDATION_FAILED", message));
    }

    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<ErrorResponse> internal(RuntimeException ex) {
        log.error("Unhandled request failure", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(500, "INTERNAL_SERVER_ERROR", "Unexpected server error"));
    }

    private static String format(FieldError error) {
        return error.getField() + " " + error.getDefaultMessage();
    }
}
