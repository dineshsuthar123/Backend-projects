"""Test the actual backup/restore/update/rollback shell scripts against the local test project."""
import json
import os
import pathlib
import shutil
import subprocess
import sys
import uuid

HERE = pathlib.Path(__file__).resolve().parent

def main():
    info = json.loads((HERE / "reports/local-stack.json").read_text())
    if not info["project"].startswith("shortener-acceptance-"):
        raise SystemExit("Requires an isolated local acceptance stack")
    env = os.environ | {"ENV_FILE": info["env_file"], "COMPOSE_PROJECT_NAME": info["project"], "DEPLOY_WAIT_TIMEOUT": "60"}
    bash = shutil.which("bash") if os.name != "nt" else "C:/Program Files/Git/bin/bash.exe"
    def shell(name, *args, success=True):
        result = subprocess.run([bash, str(HERE / name), *args], env=env, capture_output=True, text=True)
        if (result.returncode == 0) != success:
            # Credential-free operational errors only. Never echo the environment.
            print(result.stdout[-2000:]); print(result.stderr[-3000:])
            raise RuntimeError(name + " returned an unexpected status")
        return result.stdout.strip()
    backup = shell("backup.sh").splitlines()[-1]
    if os.name == "nt" and backup.startswith("/") and backup[2:3] == "/":
        backup = backup[1].upper() + ":" + backup[2:]
    shell("restore.sh", backup, "shortener_restore_ops_" + uuid.uuid4().hex[:8])
    print("PASS: actual backup.sh and restore.sh", flush=True)
    values = dict(line.split("=", 1) for line in pathlib.Path(info["env_file"]).read_text().splitlines() if line and not line.startswith("#"))
    original = values["APP_IMAGE"]
    base = info["project"] + ":ops-base"
    subprocess.run(["docker", "tag", original, base], check=True)
    release = info["project"] + ":release2"
    broken = info["project"] + ":broken"
    for tag, tail in ((release, "LABEL acceptance.release=2\n"), (broken, 'ENTRYPOINT ["/bin/false"]\n')):
        subprocess.run(["docker", "build", "-q", "-t", tag, "-"], input=("FROM " + base + "\n" + tail).encode(), check=True)
    shell("update.sh", release)
    shell("rollback.sh")
    print("PASS: valid release update and explicit image rollback", flush=True)
    shell("update.sh", broken, success=False)
    subprocess.run([sys.executable, str(HERE / "verify.py"), "--base-url", "http://127.0.0.1:18080", "--env-file", info["env_file"]], env=env, check=True)
    print("PASS: unhealthy release rejected and previous image automatically recovered", flush=True)

if __name__ == "__main__":
    main()
