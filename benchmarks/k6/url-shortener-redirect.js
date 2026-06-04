import http from "k6/http";
import { check, sleep } from "k6";

export const options = {
  scenarios: {
    redirects: {
      executor: "constant-vus",
      vus: Number(__ENV.VUS || 50),
      duration: __ENV.DURATION || "60s",
    },
  },
  thresholds: {
    http_req_duration: ["p(50)<20", "p(95)<75", "p(99)<150"],
    http_req_failed: ["rate<0.01"],
  },
  summaryTrendStats: ["min", "avg", "p(50)", "p(95)", "p(99)", "max"],
};

const baseUrl = __ENV.BASE_URL || "http://localhost:8081";

export function setup() {
  const response = http.post(
    `${baseUrl}/api/v1/urls`,
    JSON.stringify({ originalUrl: "https://spring.io/projects/spring-boot" }),
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
  });

  check(response, {
    "redirect is 302": (r) => r.status === 302,
    "location is original url": (r) => r.headers.Location === "https://spring.io/projects/spring-boot",
  });

  sleep(0.01);
}
