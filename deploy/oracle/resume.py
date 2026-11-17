"""Resume through SupplyPrint's existing guarded VM owner; never creates a second provisioner.

Default is read-only. --launch delegates exactly one retry only if the approved A1
capacity is available and the saved owner/network/billing evidence pass its guards.
"""
import argparse
import json
import pathlib
import subprocess
import sys
from capacity import check, A1

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--supplyprint", required=True, type=pathlib.Path)
    p.add_argument("--config", default="~/.oci/config")
    p.add_argument("--profile", default="DEFAULT")
    p.add_argument("--launch", action="store_true")
    args = p.parse_args()
    inventory = check(args.config, args.profile, args.supplyprint)
    if not inventory.get("saved_network_verified"):
        raise SystemExit("Saved SupplyPrint network does not match live inventory; review before any launch")
    if inventory["instances"]:
        if inventory["a1_ocpus"] == 2 and inventory["a1_ram_gb"] == 12:
            print("Existing A1 allocation found. Reuse the owner's VM; no launch performed.")
            return
        raise SystemExit("Existing instances require review; no allocation performed")
    if inventory["storage_gb"] + 50 > 200:
        raise SystemExit("Storage would exceed the approved combined free allowance")
    available = any(x["shape"] == A1 and x["status"] == "AVAILABLE" for x in inventory["capacity"])
    if not available:
        raise SystemExit("BLOCKED: Hyderabad A1 2 OCPU / 12 GB is not AVAILABLE. No launch attempted; keep the read-only watcher.")
    if not args.launch:
        print("Approved A1 capacity reported. Review SHARED-VM.md, then use --launch for one guarded owner retry.")
        return
    oracle = args.supplyprint / "deploy/oracle"
    state = json.loads((oracle / "state/provisioned.json").read_text())
    if state.get("deployment") not in ("BLOCKED_NO_A1_HOST_CAPACITY", "NETWORK_READY_VM_PENDING"):
        raise SystemExit("Owner launch state is not safely retryable")
    # The existing owner enforces tenancy-wide 2/12/200 ceilings again immediately
    # before launch and refuses stale billing evidence or uncertain prior outcomes.
    subprocess.run([sys.executable, str(oracle / "oci-infra.py"), "retry-launch", "--config", args.config,
        "--profile", args.profile, "--console-evidence", str(oracle / "state/console-evidence.json"),
        "--ssh-public-key", str(oracle / "state/operator_ed25519.pub"), "--ssh-cidr", state["ssh_source_cidr"]], check=True)
    print("Owner retry completed. Read its saved instance state; proceed to bootstrap and DNS only after reviewing access.")

if __name__ == "__main__":
    main()
