"""Start an isolated complete stack for acceptance. Never touches OCI or SupplyPrint.

The --stop mode preserves PostgreSQL volumes. Report files and credentials are ignored.
"""
import argparse
import datetime as dt
import json
import os
import pathlib
import subprocess
import sys
from importlib.util import spec_from_file_location, module_from_spec

HERE = pathlib.Path(__file__).resolve().parent
STATE = HERE / "reports/local-stack.json"

def run(command, env=None):
    return subprocess.run(command, check=True, env=env)

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--image", default="shortener:oracle-local")
    p.add_argument("--stop", action="store_true")
    args = p.parse_args()
    if args.stop:
        info = json.loads(STATE.read_text())
        env = os.environ | {"COMPOSE_PROJECT_NAME": info["project"]}
        for files, project in ((["compose.proxy.yml", "compose.proxy.test.yml"], info["project"] + "-proxy"),
                               (["compose.yml", "compose.test.yml"], info["project"])):
            command = ["docker", "compose", "--env-file", info["env_file"], "-p", project]
            for file in files:
                command += ["-f", str(HERE / file)]
            run(command + ["down"], env)
        run(["docker", "network", "rm", info["network"]])
        print("Only the acceptance containers/network stopped. Database volumes preserved.")
        return
    HERE.joinpath("reports").mkdir(exist_ok=True)
    project = "shortener-acceptance-" + dt.datetime.now(dt.timezone.utc).strftime("%Y%m%d%H%M%S")
    env_file = HERE / "reports" / (project + ".env")
    run([sys.executable, str(HERE / "generate-env.py"), "--domain", "short.example.test", "--email",
         "operator@example.invalid", "--image", args.image, "--platform", "linux/amd64", "--output", str(env_file)])
    network = project + "-edge"
    env_file.write_text(env_file.read_text().replace("EDGE_NETWORK=portfolio-edge", "EDGE_NETWORK=" + network))
    env = os.environ | {"COMPOSE_PROJECT_NAME": project}
    run([sys.executable, str(HERE / "security-check.py"), "--env-file", str(env_file)], env)
    run(["docker", "network", "create", "--subnet", "172.30.10.0/24", network])
    info = {"project": project, "env_file": str(env_file), "network": network}
    STATE.write_text(json.dumps(info, indent=2) + "\n")
    command = ["docker", "compose", "--env-file", str(env_file), "-p", project,
               "-f", str(HERE / "compose.yml"), "-f", str(HERE / "compose.test.yml")]
    run(command + ["up", "-d", "--wait", "--wait-timeout", "180"], env)
    proxy = ["docker", "compose", "--env-file", str(env_file), "-p", project + "-proxy",
             "-f", str(HERE / "compose.proxy.yml"), "-f", str(HERE / "compose.proxy.test.yml")]
    run([sys.executable, str(HERE / "validate-caddy.py"), "--env-file", str(env_file)], env)
    run(proxy + ["up", "-d"], env)
    run([sys.executable, str(HERE / "verify.py"), "--base-url", "http://127.0.0.1:18080",
         "--env-file", str(env_file), "--local-operations"], env)
    print("Local stack remains running for browser and shell operation tests. Use --stop afterwards.")

if __name__ == "__main__":
    main()
