package com.naresh.urlshortener.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class UrlPolicyTest {
    @ParameterizedTest
    @ValueSource(strings={"javascript:alert(1)", "data:text/html,hello", "file:///etc/passwd", "//example.com", "https://user:pass@example.com", "https://example.com\r\nLocation:evil", "https://example.com\\@evil.com", "https://example.com:99999"})
    void rejectsUnsafeDestinations(String value) {
        assertThatThrownBy(() -> UrlPolicy.validate(value)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @ParameterizedTest @ValueSource(strings={"actuator", "error", "API", "//evil.com", "bad alias"})
    void rejectsReservedAndUnsafeAliases(String alias) {
        assertThatThrownBy(() -> UrlPolicy.validateAlias(alias)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @Test void acceptsHttpsQueriesAndLongAliases() {
        assertThat(UrlPolicy.validate("https://example.com/a?b=c#d").getHost()).isEqualTo("example.com");
        UrlPolicy.validateAlias("a".repeat(32));
    }
}
