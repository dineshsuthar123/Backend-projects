package com.naresh.urlshortener.service;

public class UrlNotFoundException extends RuntimeException {

    public UrlNotFoundException(String shortCode) {
        super("Short URL not found: " + shortCode);
    }
}
