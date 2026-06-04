package com.naresh.urlshortener.api;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CreateShortUrlRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsHttpUrlAndOneSecondTtl() {
        CreateShortUrlRequest request = new CreateShortUrlRequest("https://example.com/a", "alias1", 1L);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsNonHttpUrl() {
        CreateShortUrlRequest request = new CreateShortUrlRequest("ftp://example.com/a", null, null);

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void rejectsInvalidAliasCharacters() {
        CreateShortUrlRequest request = new CreateShortUrlRequest("https://example.com/a", "bad alias", null);

        assertThat(validator.validate(request)).isNotEmpty();
    }
}
