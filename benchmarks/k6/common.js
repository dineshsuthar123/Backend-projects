import http from "k6/http";
import { check } from "k6";

export const baseUrl = __ENV.BASE_URL || "http://localhost:8081";
export const targetUrl = __ENV.TARGET_URL || "https://spring.io/projects/spring-boot";

export function syntheticIp() {
  const vu = typeof __VU === "number" ? __VU : 0;
  const iter = typeof __ITER === "number" ? __ITER : 0;
  const sequence = (vu * 100000 + iter) % 50000;
  const third = Math.floor(sequence / 240) % 200;
  const fourth = (sequence % 240) + 10;
  return `10.20.${third}.${fourth}`;
}

export function uniqueAlias(prefix) {
  return `${prefix}_${__VU}_${__ITER}_${Date.now()}`.replace(/[^A-Za-z0-9_-]/g, "").slice(0, 32);
}

export function createUrl(alias, ttlSeconds, originalUrl = targetUrl) {
  const body = { originalUrl };
  if (alias) {
    body.customAlias = alias;
  }
  if (ttlSeconds) {
    body.ttlSeconds = ttlSeconds;
  }
  const response = http.post(`${baseUrl}/api/v1/urls`, JSON.stringify(body), {
    headers: { "Content-Type": "application/json", "X-Forwarded-For": syntheticIp() },
    tags: { name: "create_short_url" },
  });
  check(response, {
    "created short url": (r) => r.status === 201,
  });
  return response.json();
}

export function evictCache(shortCode) {
  return http.del(`${baseUrl}/api/v1/urls/${shortCode}/cache`, null, {
    headers: { "X-Forwarded-For": syntheticIp() },
    tags: { name: "evict_cache" },
  });
}

export function redirect(shortCode, expectedUrl = targetUrl, name = "redirect") {
  const response = http.get(`${baseUrl}/${shortCode}`, {
    redirects: 0,
    headers: { "X-Forwarded-For": syntheticIp() },
    tags: { name },
  });
  check(response, {
    "redirect is 302": (r) => r.status === 302,
    "location is original url": (r) => r.headers.Location === expectedUrl,
  });
  return response;
}
