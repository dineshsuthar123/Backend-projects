package com.naresh.urlshortener.api;

import com.naresh.urlshortener.service.AliasAlreadyExistsException;
import com.naresh.urlshortener.service.UrlExpiredException;
import com.naresh.urlshortener.service.UrlNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void notFoundUsesStableCode() {
        ResponseEntity<ErrorResponse> response = handler.notFound(new UrlNotFoundException("abc12345"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("SHORT_CODE_NOT_FOUND");
    }

    @Test
    void expiredUsesStableCode() {
        ResponseEntity<ErrorResponse> response = handler.expired(new UrlExpiredException("abc12345"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(response.getBody().code()).isEqualTo("SHORT_URL_EXPIRED");
    }

    @Test
    void duplicateAliasUsesStableCode() {
        ResponseEntity<ErrorResponse> response = handler.aliasConflict(new AliasAlreadyExistsException("abc12345"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("CUSTOM_ALIAS_ALREADY_EXISTS");
    }

    @Test
    void runtimeErrorDoesNotExposeExceptionDetails() {
        ResponseEntity<ErrorResponse> response = handler.internal(new RuntimeException("secret"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(response.getBody().message()).doesNotContain("secret");
    }
}
