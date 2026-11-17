"""Validate rendered production Compose without printing its secret environment."""
import json
import pathlib
import subprocess
import argparse
import datetime as dt
import re

ROOT = pathlib.Path(__file__).resolve().parents[2]
HERE = ROOT / "deploy/oracle"

def require(condition, message):
    if not condition:
        raise SystemExit(message)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", type=pathlib.Path, default=HERE / ".env")
    args = parser.parse_args()
    for filename in ("compose.yml", "compose.proxy.yml"):
        result = subprocess.run(["docker", "compose", "--env-file", str(args.env_file),
            "-f", str(HERE / filename), "config", "--format", "json"], check=True, capture_output=True)
        config = json.loads(result.stdout)
        for name, service in config["services"].items():
            require(service.get("restart") == "unless-stopped", f"Restart policy missing: {name}")
            require(int(service.get("mem_limit", 0)) > 0, f"Memory limit missing: {name}")
            require(service.get("logging", {}).get("options", {}).get("max-file") == "3", f"Log rotation missing: {name}")
            if name != "caddy":
                require(not service.get("ports"), f"Internal service published: {name}")
            else:
                require({int(x["published"]) for x in service["ports"]} == {80, 443}, "Unexpected public ports")
    frontend = (ROOT / "src/main/resources/static/app.js").read_text()
    code = "\n".join(p.read_text() for p in (ROOT / "src/main/java").rglob("*.java"))
    require(not re.search(r"XsltView|SseEmitter|FragmentsRendering|text/event-stream|ServerSentEvent|ModelAndView", code),
            "Reassess the scoped Spring vulnerability applicability statements before using view rendering or SSE")
    require(dt.date.today() <= dt.date(2026, 12, 17), "Spring vulnerability applicability review expired; refresh dependencies and review AUDIT.md")
    require("innerHTML" not in frontend and "insertAdjacentHTML" not in frontend, "Unsafe frontend HTML sink")
    require("X-Management-Key" not in frontend, "Browser must not receive management credentials")
    dockerfile = (HERE / "Dockerfile").read_text()
    require("USER 10001:10001" in dockerfile and "-Xmx384m" in dockerfile, "Container user or JVM limit missing")
    for name in ("Dockerfile", "compose.yml", "compose.proxy.yml"):
        for line in (HERE / name).read_text().splitlines():
            if ("image:" in line and "APP_IMAGE" not in line) or line.startswith("FROM "):
                require("@sha256:" in line, f"Unpinned dependency in {name}")
    print("Production exposure, resource limits, log rotation, image pins and frontend security checks passed.")

if __name__ == "__main__":
    main()
