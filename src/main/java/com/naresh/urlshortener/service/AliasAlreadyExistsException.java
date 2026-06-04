package com.naresh.urlshortener.service;

public class AliasAlreadyExistsException extends RuntimeException {

    public AliasAlreadyExistsException(String shortCode) {
        super("Short code already exists: " + shortCode);
    }
}
