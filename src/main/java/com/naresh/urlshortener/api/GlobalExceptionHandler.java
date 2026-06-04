package com.naresh.urlshortener.api;

import com.naresh.urlshortener.service.AliasAlreadyExistsException;
import com.naresh.urlshortener.service.UrlExpiredException;
import com.naresh.urlshortener.service.UrlNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(UrlNotFoundException.class)
    ResponseEntity<ErrorResponse> notFound(UrlNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, "not_found", ex.getMessage()));
    }

    @ExceptionHandler(UrlExpiredException.class)
    ResponseEntity<ErrorResponse> expired(UrlExpiredException ex) {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(ErrorResponse.of(410, "gone", ex.getMessage()));
    }

    @ExceptionHandler(AliasAlreadyExistsException.class)
    ResponseEntity<ErrorResponse> aliasConflict(AliasAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, "conflict", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::format)
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "bad_request", message));
    }

    private static String format(FieldError error) {
        return error.getField() + " " + error.getDefaultMessage();
    }
}
