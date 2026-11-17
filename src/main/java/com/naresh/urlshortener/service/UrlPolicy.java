package com.naresh.urlshortener.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.util.Locale;
import java.util.Set;

public final class UrlPolicy {
    private static final Set<String> RESERVED = Set.of("actuator", "api", "error", "index", "robots", "favicon");
    private UrlPolicy() { }
    public static URI validate(String value) {
        try {
            URI uri = URI.create(value);
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null
                    || uri.getUserInfo() != null || value.chars().anyMatch(c -> c <= 32 || c == 127)
                    || value.contains("\\") || uri.getPort() > 65535) throw new IllegalArgumentException();
            return uri;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use an absolute HTTP or HTTPS URL without credentials or control characters");
        }
    }
    public static void validateAlias(String alias) {
        if (alias != null && (!alias.matches("[A-Za-z0-9_-]{4,32}") || RESERVED.contains(alias.toLowerCase(Locale.ROOT)))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or reserved custom alias");
        }
    }
}
