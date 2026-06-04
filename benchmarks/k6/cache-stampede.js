import { sleep } from "k6";
import { createUrl, evictCache, redirect } from "./common.js";

export const options = {
  scenarios: {
    cold_popular_url: {
      executor: "shared-iterations",
      vus: Number(__ENV.VUS || 100),
      iterations: Number(__ENV.ITERATIONS || 100),
      maxDuration: __ENV.DURATION || "30s",
    },
  },
  thresholds: {
    "http_req_duration{name:cache_stampede_redirect}": ["p(95)<250", "p(99)<500"],
    "http_req_failed{name:cache_stampede_redirect}": ["rate<0.01"],
  },
  summaryTrendStats: ["min", "avg", "p(50)", "p(95)", "p(99)", "max"],
};

export function setup() {
  const created = createUrl();
  evictCache(created.shortCode);
  return created;
}

export default function (created) {
  redirect(created.shortCode, created.originalUrl, "cache_stampede_redirect");
  sleep(0.001);
}
