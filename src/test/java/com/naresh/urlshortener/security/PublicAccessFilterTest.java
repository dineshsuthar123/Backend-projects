package com.naresh.urlshortener.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.naresh.urlshortener.config.PublicAccessProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

class PublicAccessFilterTest {
    private static final String KEY = "test-management-key-01234567890123456789";
    private final PublicAccessFilter filter = new PublicAccessFilter(new PublicAccessProperties(true, KEY, true,
            Set.of("example.com"), 100, 10, 10000), new ObjectMapper().registerModule(new JavaTimeModule()));

    @Test void managementCreationRequiresPrivateKey() throws Exception {
        assertThat(run("POST", "/api/v1/urls", null, "{}").getStatus()).isEqualTo(401);
        assertThat(run("POST", "/api/v1/urls", "incorrect", "{}").getStatus()).isEqualTo(401);
        assertThat(run("POST", "/api/v1/urls", KEY, "{}").getStatus()).isEqualTo(200);
    }
    @Test void cacheEvictionRequiresPrivateKey() throws Exception {
        assertThat(run("DELETE", "/api/v1/urls/abcd/cache", null, "").getStatus()).isEqualTo(401);
    }
    @Test void encodedOrAmbiguousPathsCannotBypassManagementAuthorization() throws Exception {
        for (String path : java.util.List.of("/api/v1/%75rls", "/api/v1/urls;x", "//api/v1/urls")) {
            assertThat(run("POST", path, null, "{}").getStatus()).isEqualTo(400);
        }
    }
    @Test void publicRedirectAndStatsRemainReadable() throws Exception {
        assertThat(run("GET", "/abcd", null, "").getStatus()).isEqualTo(200);
        assertThat(run("GET", "/api/v1/urls/abcd/stats", null, "").getStatus()).isEqualTo(200);
    }
    @Test void diagnosticsRemainHiddenEvenWithManagementCredential() throws Exception {
        assertThat(run("GET", "/actuator/prometheus", KEY, "").getStatus()).isEqualTo(404);
        assertThat(run("GET", "/actuator/env", KEY, "").getStatus()).isEqualTo(404);
        assertThat(run("GET", "/actuator/health/readiness", null, "").getStatus()).isEqualTo(200);
    }
    @Test void oversizedChunkedBodyIsBoundedByActualBytes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/demo/urls") {
            @Override public int getContentLength() { return -1; }
            @Override public long getContentLengthLong() { return -1; }
        };
        request.setContent(new byte[8193]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(413);
    }
    @Test void boundedBodyIsStillReadableByController() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/demo/urls");
        request.setContent("{\"originalUrl\":\"https://example.com\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> assertThat(new String(req.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8)).contains("https://example.com"));
        assertThat(response.getHeader("Content-Security-Policy")).contains("frame-ancestors 'none'");
    }
    @Test void weakCredentialFailsStartup() {
        assertThatThrownBy(() -> new PublicAccessProperties(true, "short", true, null, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
    private MockHttpServletResponse run(String method, String path, String key, String body) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        if (key != null) request.addHeader("X-Management-Key", key);
        request.setContent(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
