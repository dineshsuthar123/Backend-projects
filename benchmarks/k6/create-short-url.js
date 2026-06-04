import http from "k6/http";
import { check, sleep } from "k6";
import { baseUrl, syntheticIp, targetUrl, uniqueAlias } from "./common.js";

export const options = {
  scenarios: {
    creates: {
      executor: "constant-arrival-rate",
      rate: Number(__ENV.RPS || 100),
      timeUnit: "1s",
      duration: __ENV.DURATION || "60s",
      preAllocatedVUs: Number(__ENV.VUS || 50),
      maxVUs: Number(__ENV.MAX_VUS || 150),
    },
  },
  thresholds: {
    "http_req_duration{name:create_short_url}": ["p(50)<75", "p(95)<250", "p(99)<500"],
    "http_req_failed{name:create_short_url}": ["rate<0.01"],
  },
  summaryTrendStats: ["min", "avg", "p(50)", "p(95)", "p(99)", "max"],
};

export default function () {
  const payload = {
    originalUrl: `${targetUrl}?vu=${__VU}&iter=${__ITER}`,
  };
  if (__ENV.CUSTOM_ALIAS === "true") {
    payload.customAlias = uniqueAlias("create");
  }

  const response = http.post(
    `${baseUrl}/api/v1/urls`,
    JSON.stringify(payload),
    {
      headers: { "Content-Type": "application/json", "X-Forwarded-For": syntheticIp() },
      tags: { name: "create_short_url" },
    }
  );

  check(response, {
    "created short url": (r) => r.status === 201,
  });
  sleep(0.01);
}
