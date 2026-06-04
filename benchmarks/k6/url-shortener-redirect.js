import http from "k6/http";
import { check, sleep } from "k6";

export const options = {
  scenarios: {
    redirects: {
      executor: "constant-arrival-rate",
      rate: Number(__ENV.RPS || 80),
      timeUnit: "1s",
      duration: __ENV.DURATION || "60s",
      preAllocatedVUs: Number(__ENV.VUS || 50),
      maxVUs: Number(__ENV.MAX_VUS || 100),
    },
  },
  thresholds: {
    http_req_duration: ["p(50)<20", "p(95)<75", "p(99)<150"],
    http_req_failed: ["rate<0.01"],
  },
  summaryTrendStats: ["min", "avg", "p(50)", "p(95)", "p(99)", "max"],
};

const baseUrl = __ENV.BASE_URL || "http://localhost:8081";
const originalUrl = "https://spring.io/projects/spring-boot";

function clientIp() {
  const lastOctet = (__VU % 240) + 10;
  return `10.10.0.${lastOctet}`;
}

export function setup() {
  const response = http.post(
    `${baseUrl}/api/v1/urls`,
    JSON.stringify({ originalUrl }),
    { headers: { "Content-Type": "application/json" } }
  );

  check(response, {
    "created short url": (r) => r.status === 201,
  });

  return response.json();
}

export default function (created) {
  const response = http.get(`${baseUrl}/${created.shortCode}`, {
    redirects: 0,
    headers: {
      "X-Forwarded-For": clientIp(),
    },
  });

  check(response, {
    "redirect is 302": (r) => r.status === 302,
    "location is original url": (r) => r.headers.Location === originalUrl,
  });

  sleep(0.01);
}
