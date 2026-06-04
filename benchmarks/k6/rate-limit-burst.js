import http from "k6/http";
import { check, sleep } from "k6";
import { baseUrl, createUrl } from "./common.js";

http.setResponseCallback(http.expectedStatuses({ min: 200, max: 499 }));

export const options = {
  scenarios: {
    burst: {
      executor: "constant-arrival-rate",
      rate: Number(__ENV.RPS || 500),
      timeUnit: "1s",
      duration: __ENV.DURATION || "30s",
      preAllocatedVUs: Number(__ENV.VUS || 100),
      maxVUs: Number(__ENV.MAX_VUS || 250),
    },
  },
  thresholds: {
    "http_req_duration{name:rate_limit_burst}": ["p(95)<100"],
  },
  summaryTrendStats: ["min", "avg", "p(50)", "p(95)", "p(99)", "max"],
};

export function setup() {
  return createUrl();
}

export default function (created) {
  const response = http.get(`${baseUrl}/${created.shortCode}`, {
    redirects: 0,
    headers: { "X-Forwarded-For": __ENV.CLIENT_IP || "10.99.0.1" },
    tags: { name: "rate_limit_burst" },
  });
  check(response, {
    "allowed or rate limited": (r) => r.status === 302 || r.status === 429,
    "429 includes retry-after": (r) => r.status !== 429 || Boolean(r.headers["Retry-After"]),
  });
  sleep(0.001);
}
