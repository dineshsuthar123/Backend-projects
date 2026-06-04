import http from "k6/http";
import { check, sleep } from "k6";
import { baseUrl, createUrl, syntheticIp } from "./common.js";

http.setResponseCallback(http.expectedStatuses({ min: 200, max: 499 }));

export const options = {
  scenarios: {
    ttl_expiry: {
      executor: "shared-iterations",
      vus: 1,
      iterations: 1,
      maxDuration: "20s",
    },
  },
  thresholds: {
    "http_req_duration{name:ttl_expired_redirect}": ["p(95)<100"],
  },
  summaryTrendStats: ["min", "avg", "p(50)", "p(95)", "p(99)", "max"],
};

export function setup() {
  const created = createUrl(null, 2);
  sleep(3);
  return created;
}

export default function (created) {
  const response = http.get(`${baseUrl}/${created.shortCode}`, {
    redirects: 0,
    headers: { "X-Forwarded-For": syntheticIp() },
    tags: { name: "ttl_expired_redirect" },
  });
  check(response, {
    "expired url returns 410": (r) => r.status === 410,
    "uses machine readable code": (r) => r.json("code") === "SHORT_URL_EXPIRED",
  });
}
