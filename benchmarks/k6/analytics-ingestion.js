import { sleep } from "k6";
import { createUrl, redirect } from "./common.js";

export const options = {
  scenarios: {
    analytics_redirects: {
      executor: "constant-arrival-rate",
      rate: Number(__ENV.RPS || 250),
      timeUnit: "1s",
      duration: __ENV.DURATION || "60s",
      preAllocatedVUs: Number(__ENV.VUS || 100),
      maxVUs: Number(__ENV.MAX_VUS || 250),
    },
  },
  thresholds: {
    "http_req_duration{name:analytics_redirect}": ["p(50)<25", "p(95)<100", "p(99)<200"],
    "http_req_failed{name:analytics_redirect}": ["rate<0.01"],
  },
  summaryTrendStats: ["min", "avg", "p(50)", "p(95)", "p(99)", "max"],
};

export function setup() {
  return createUrl();
}

export default function (created) {
  redirect(created.shortCode, created.originalUrl, "analytics_redirect");
  sleep(0.001);
}
