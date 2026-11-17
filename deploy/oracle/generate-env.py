"""Create private credentials exactly once. Never prints secret values."""
import argparse
import os
import pathlib
import re
import secrets

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--domain", required=True)
    p.add_argument("--email", required=True)
    p.add_argument("--image", required=True)
    p.add_argument("--platform", default="linux/arm64", choices=["linux/arm64", "linux/amd64"])
    p.add_argument("--output", type=pathlib.Path, default=pathlib.Path(__file__).parent / ".env")
    args = p.parse_args()
    if not re.fullmatch(r"[a-zA-Z0-9][a-zA-Z0-9.-]+\.[a-zA-Z]{2,}", args.domain):
        p.error("Provide a DNS hostname")
    if not re.fullmatch(r"[^\s=@]+@[^\s=@]+\.[^\s=@]+", args.email) or not re.fullmatch(r"[A-Za-z0-9_./:@-]+", args.image):
        p.error("Invalid email or image")
    values = dict(APP_DOMAIN=args.domain, ACME_EMAIL=args.email, APP_IMAGE=args.image,
                  PLATFORM=args.platform, EDGE_NETWORK="portfolio-edge", DB_NAME="shortener")
    for name in ("DB_ADMIN_PASSWORD", "DB_APP_PASSWORD", "DB_MIGRATION_PASSWORD", "MANAGEMENT_API_KEY", "ANALYTICS_HASH_SALT"):
        values[name] = secrets.token_hex(32)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    fd = os.open(args.output, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as f:
        f.write("".join(f"{k}={v}\n" for k, v in values.items()))
    print("Private environment created; credentials were not printed. Restrict its Windows ACL if stored on Windows.")

if __name__ == "__main__":
    main()
