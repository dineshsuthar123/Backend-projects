import { sleep } from "k6";
import { createUrl, redirect } from "./common.js";

export const options = {
  scenarios: {
    cache_hit_redirects: {
      executor: "constant-arrival-rate",
      rate: Number(__ENV.RPS || 80),
      timeUnit: "1s",
      duration: __ENV.DURATION || "60s",
      preAllocatedVUs: Number(__ENV.VUS || 50),
      maxVUs: Number(__ENV.MAX_VUS || 100),
    },
  },
  thresholds: {
    "http_req_duration{name:redirect_cache_hit}": ["p(50)<20", "p(95)<75", "p(99)<150"],
    "http_req_failed{name:redirect_cache_hit}": ["rate<0.01"],
  },
  summaryTrendStats: ["min", "avg", "p(50)", "p(95)", "p(99)", "max"],
};

export function setup() {
  return createUrl();
}

export default function (created) {
  redirect(created.shortCode, created.originalUrl, "redirect_cache_hit");
  sleep(0.01);
}
