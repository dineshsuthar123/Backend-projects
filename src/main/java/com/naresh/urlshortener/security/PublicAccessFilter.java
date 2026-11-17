package com.naresh.urlshortener.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naresh.urlshortener.api.ErrorResponse;
import com.naresh.urlshortener.config.PublicAccessProperties;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
@Order(-100)
public class PublicAccessFilter extends OncePerRequestFilter {
    private final PublicAccessProperties properties;
    private final ObjectMapper mapper;
    public PublicAccessFilter(PublicAccessProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; "
                + "img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
        if (!properties.enabled()) { chain.doFilter(request, response); return; }
        String rawPath = request.getRequestURI();
        // These routes use only ASCII names. Reject ambiguous encodings/path parameters
        // before MVC normalization can turn an unprotected spelling into a protected route.
        if (rawPath.contains("%") || rawPath.contains(";") || rawPath.contains("//") || rawPath.contains("\\")) {
            error(response, 400, "INVALID_PATH", "Use a canonical request path"); return;
        }
        String path = request.getServletPath();
        if (path == null || path.isEmpty()) path = rawPath;
        if (path.startsWith("/actuator") && !path.matches("/actuator/health(/(liveness|readiness))?")) {
            error(response, 404, "NOT_FOUND", "Not found"); return;
        }
        boolean readOnly = java.util.Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod());
        boolean publicDemo = "POST".equals(request.getMethod()) && path.equals("/api/v1/demo/urls");
        boolean management = !readOnly && !publicDemo;
        if (management && !authorized(request.getHeader("X-Management-Key"))) {
            error(response, 401, "UNAUTHORIZED", "A private management key is required"); return;
        }
        if (path.startsWith("/api/") && ("POST".equals(request.getMethod()) || "PUT".equals(request.getMethod()))) {
            // Bound the actual stream too: Content-Length can be absent for chunked requests.
            byte[] body = request.getInputStream().readNBytes(8193);
            if (body.length > 8192) { error(response, 413, "REQUEST_TOO_LARGE", "Maximum request size is 8 KiB"); return; }
            request = new HttpServletRequestWrapper(request) {
                @Override public ServletInputStream getInputStream() {
                    ByteArrayInputStream input = new ByteArrayInputStream(body);
                    return new ServletInputStream() {
                        @Override public int read() { return input.read(); }
                        @Override public boolean isFinished() { return input.available() == 0; }
                        @Override public boolean isReady() { return true; }
                        @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
                    };
                }
                @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8)); }
            };
        }
        chain.doFilter(request, response);
    }

    private boolean authorized(String supplied) {
        return supplied != null && MessageDigest.isEqual(properties.managementKey().getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
    }
    private void error(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status); response.setContentType("application/json");
        mapper.writeValue(response.getWriter(), ErrorResponse.of(status, code, message));
    }
}
