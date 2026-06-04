import { sleep } from "k6";
import { createUrl, redirect } from "./common.js";

export const options = {
  scenarios: {
    stress: {
      executor: "ramping-arrival-rate",
      startRate: Number(__ENV.START_RPS || 100),
      timeUnit: "1s",
      preAllocatedVUs: Number(__ENV.VUS || 150),
      maxVUs: Number(__ENV.MAX_VUS || 500),
      stages: [
        { target: Number(__ENV.RPS_1 || 250), duration: __ENV.STAGE_1 || "30s" },
        { target: Number(__ENV.RPS_2 || 500), duration: __ENV.STAGE_2 || "30s" },
        { target: Number(__ENV.RPS_3 || 1000), duration: __ENV.STAGE_3 || "30s" },
        { target: Number(__ENV.RPS_4 || 1500), duration: __ENV.STAGE_4 || "30s" },
      ],
    },
  },
  thresholds: {
    "http_req_duration{name:stress_redirect}": ["p(99)<1000"],
  },
  summaryTrendStats: ["min", "avg", "p(50)", "p(95)", "p(99)", "max"],
};

export function setup() {
  return createUrl();
}

export default function (created) {
  redirect(created.shortCode, created.originalUrl, "stress_redirect");
  sleep(0.001);
}
