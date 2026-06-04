import { sleep } from "k6";
import { createUrl, evictCache, redirect } from "./common.js";

export const options = {
  scenarios: {
    cache_miss_redirects: {
      executor: "constant-arrival-rate",
      rate: Number(__ENV.RPS || 100),
      timeUnit: "1s",
      duration: __ENV.DURATION || "60s",
      preAllocatedVUs: Number(__ENV.VUS || 60),
      maxVUs: Number(__ENV.MAX_VUS || 120),
    },
  },
  thresholds: {
    "http_req_duration{name:redirect_cache_miss}": ["p(50)<50", "p(95)<150", "p(99)<300"],
    "http_req_failed{name:redirect_cache_miss}": ["rate<0.01"],
  },
  summaryTrendStats: ["min", "avg", "p(50)", "p(95)", "p(99)", "max"],
};

export default function () {
  const created = createUrl();
  evictCache(created.shortCode);
  redirect(created.shortCode, created.originalUrl, "redirect_cache_miss");
  sleep(0.01);
}
