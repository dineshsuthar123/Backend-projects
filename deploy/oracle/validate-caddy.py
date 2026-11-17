"""Validate Caddy without claiming the shared proxy IP or starting an ACME client."""
import argparse
import json
import pathlib
import subprocess

HERE = pathlib.Path(__file__).resolve().parent

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--env-file", type=pathlib.Path, default=HERE / ".env")
    args = p.parse_args()
    raw = subprocess.check_output(["docker", "compose", "--env-file", str(args.env_file),
        "-f", str(HERE / "compose.proxy.yml"), "config", "--format", "json"])
    service = json.loads(raw)["services"]["caddy"]
    command = ["docker", "run", "--rm", "--network", "none", "--memory", "128m"]
    for name, value in service["environment"].items():
        command += ["--env", name + "=" + value]
    for volume in service["volumes"]:
        if volume["type"] == "bind":
            command += ["--mount", f"type=bind,source={volume['source']},target={volume['target']},readonly"]
    subprocess.run(command + [service["image"], "caddy", "validate", "--config", "/etc/caddy/Caddyfile", "--adapter", "caddyfile"], check=True)

if __name__ == "__main__":
    main()
