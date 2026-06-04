package com.naresh.urlshortener.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShortCodeGeneratorTest {

    @Test
    void generatesBase62CodesWithRequestedLength() {
        ShortCodeGenerator generator = new ShortCodeGenerator();

        String code = generator.generate(8);

        assertThat(code).hasSize(8);
        assertThat(code).matches("^[A-Za-z0-9]+$");
    }
}
