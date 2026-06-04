package com.naresh.urlshortener.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record CreateShortUrlRequest(
        @NotBlank @URL @Pattern(regexp = "^https?://.*$") @Size(max = 4096) String originalUrl,
        @Pattern(regexp = "^[A-Za-z0-9_-]{4,32}$") String customAlias,
        @Min(1) @Max(31_536_000) Long ttlSeconds
) {
}
