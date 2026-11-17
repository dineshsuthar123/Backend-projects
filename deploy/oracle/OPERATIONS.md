# Oracle operations

Read [AUDIT.md](AUDIT.md), [SHARED-VM.md](SHARED-VM.md) and [STATUS.md](STATUS.md) first. These commands target a reviewed Linux ARM64 Ubuntu host. Never use `docker-compose.prod.yml` or the older untracked deployment workflow as the Oracle production entry point.

## Capacity and the saved retry

On Windows PowerShell, using the existing private OCI SDK environment:

```powershell
$supply = 'C:\Users\Naresh Suthar\vscode projects\supply\supplychain-auth-oracle'
$ociPython = Join-Path $supply 'reports\oracle\oci-venv\Scripts\python.exe'
& $ociPython deploy/oracle/capacity.py --supplyprint $supply
& $ociPython deploy/oracle/resume.py --supplyprint $supply
```

The second command is read-only by default. If the exact approved A1 configuration becomes AVAILABLE, use `--launch` once. It delegates to SupplyPrint's existing saved-network retry, repeats its free-allocation checks and refuses uncertain state. Refresh the owner's Console evidence if its 24-hour guard expires. Do not change account tier, region, shape, SSH CIDR, subnet or quota ceilings to force a retry. No new scheduled watcher or failed-launch loop is needed.

If a VM already exists, reuse it after reviewing its workloads and approved SSH access. The user's explicit Free Tier account statement plus authenticated region/shape/inventory and official eligibility documentation form the cost evidence; IAM does **not** return the account billing plan. Before launch, recheck the Console shows Free Tier, not PAYG, and the selected platform image remains free eligible.

## Build and transfer a release without a registry

Require a passing `Oracle Always Free verification` run for the exact commit. CI uses the public repository's standard hosted Linux runner, `contents: read`, no OCI/SSH secrets, no registry and no artifact upload. No paid runner, registry or artifact storage is required. The actual cloud deploy is an operator SSH action from the already approved narrow source, not a runner with cloud administrator credentials.

```powershell
$release = git rev-parse HEAD
docker buildx build --platform linux/arm64 --load -f deploy/oracle/Dockerfile -t "shortener:$release" .
docker image inspect "shortener:$release" --format '{{.Architecture}}'
docker save -o shortener-image.tar "shortener:$release"
git archive --format=tar.gz --output=shortener-source.tar.gz HEAD
# Use the existing approved operator key LOCALLY; never transfer a private key.
scp -i '<operator-private-key-path>' shortener-image.tar shortener-source.tar.gz ubuntu@<VM-IP>:/tmp/
```

Archive only the tracked commit. `.env`, keys, state and backups are ignored and must not be copied into a release source archive. Existing development-only credentials/configuration are not production secrets.

## Initial host and HTTPS

Review SHARED-VM.md before changing a shared host. SSH to the reviewed VM, then:

```bash
sudo install -d -m 755 /opt/url-shortener
sudo tar -xzf /tmp/shortener-source.tar.gz -C /opt/url-shortener
cd /opt/url-shortener
sudo bash deploy/oracle/bootstrap.sh
sudo bash deploy/oracle/host-firewall.sh
sudo docker load --input /tmp/shortener-image.tar
sudo python3 deploy/oracle/generate-env.py \
  --domain YOUR_CONFIRMED_HOSTNAME --email YOUR_CERTIFICATE_EMAIL \
  --image shortener:EXACT_COMMIT_SHA
sudo bash deploy/oracle/deploy.sh
sudo python3 deploy/oracle/verify.py --base-url https://YOUR_CONFIRMED_HOSTNAME
sudo bash deploy/oracle/install-operations.sh
```

`bootstrap.sh` enables Docker at boot and prepares only the external Docker proxy network. It does not alter OCI or SSH rules. The separate firewall script adds HTTP(S) ingress without flushing rules or changing SSH; when netfilter-persistent is present it saves those rules. Verify persistence after reboot on the actual image. Production containers have restart policies; no `compose down` service is installed at shutdown.

Before `deploy.sh`, set the hostname's A record to the VM public IPv4; remove a stale AAAA record unless IPv6 is actually configured. Use an existing hostname or a free DNS account you control whose persistence/HTTPS suitability has been checked. No hostname has been fabricated or purchased, and no temporary tunnel is used. Caddy needs working public DNS and inbound TCP 80/443 for automatic certificates. `deploy.sh`/`health.sh` fail until valid public HTTPS works. Caddy volumes preserve ACME account keys and certificates. Do not overwrite another application's proxy files.

The host operator requires approved SSH and explicit `sudo` for Docker/host administration. CI has no deploy credential. Do not open SSH to all addresses, add the application UID to the Docker group, or mount the Docker socket into application containers. The database admin role is used only by local operations; runtime has its own restricted role.

## Routine commands

From `/opt/url-shortener`:

```bash
# Health, resource sample and disk floor (validates public TLS normally)
sudo bash deploy/oracle/health.sh

# Restart only the backend
sudo docker compose --env-file deploy/oracle/.env -f deploy/oracle/compose.yml restart shortener-app

# View bounded logs
sudo docker compose --env-file deploy/oracle/.env -f deploy/oracle/compose.yml logs --tail 100 -f shortener-app

# Backup the canonical database; output is a private .dump path
sudo bash deploy/oracle/backup.sh

# Restore WITHOUT changing the current database
sudo bash deploy/oracle/restore.sh /PRIVATE/BACKUP.dump shortener_restore_review_20261117

# Load the new reviewed ARM image, then update only the application
sudo docker load --input /tmp/next-shortener-image.tar
sudo bash deploy/oracle/update.sh shortener:NEW_COMMIT_SHA

# Return to the saved previous image, retaining every persistent volume
sudo bash deploy/oracle/rollback.sh
```

Never run `down -v`, delete a database volume, or use a prune operation that removes database volumes. Keep the previous image locally for rollback. The release updater backs up first, resolves immutable image IDs, waits for readiness and automatically restores the prior image when the new container is unhealthy. Flyway history and schema migrations are forward-only. V3/V4 are additive/permission changes compatible with this release; future destructive migrations require a separate maintenance/backup/compatibility plan.

If sharing with SupplyPrint later, persist `COMPOSE_EXTRA_FILE=/opt/url-shortener/deploy/oracle/compose.shared-budget.yml` and `PROXY_EXTRA_FILE=/opt/url-shortener/deploy/oracle/compose.proxy.shared-budget.yml` in the private environment **after** the reviewed migration. The shell operations consistently read them. Include the same override in any direct Compose command. SupplyPrint's own entry points must be updated together, as described in SHARED-VM.md.

## Restore cutover and backup retention

The restore script deliberately refuses the live database name. It creates a new `shortener_restore_*` database, restores with the migration owner, reapplies runtime privileges and reports mapping count. Inspect representative mappings and Flyway history; run a temporary application validation before cutover.

For an approved recovery cutover: stop only `shortener-app`, take an additional backup of the current database if readable, edit **only** `DB_NAME` in the root-owned `.env` to the verified restored name, clear the shortener's **dedicated** Redis database (to prevent stale cached mappings), recreate the app and run acceptance checks. Its original database remains available for rollback; do not drop either database automatically. Set `DB_NAME` back and clear that Redis database to reverse the cutover.

The daily systemd timer writes private custom-format dumps and SHA-256 sidecars. A local dump protects against application mistakes, not loss of the VM/boot volume. Copy verified backups to the owner's workstation over SSH at zero additional hosting cost. Review/rotate old copies only after validating the off-host copy; these scripts do not delete existing backups or application data. The 5-GiB disk floor blocks new write-heavy operations before filling the boot volume. Monitor `journalctl -u url-shortener-backup -u url-shortener-monitor` and `systemctl list-timers`; arrange operator alerts if continuous unattended monitoring is required. No paid notification service is configured.

## Functional behavior and credentials

* Frontend: `GET /`. It uses `POST /api/v1/demo/urls` for the explicitly limited demo. Alias and TTL remain supported within the published demo policy.
* General creation: `POST /api/v1/urls` with `X-Management-Key` read from a protected operator environment/file. Never paste that credential into browser code, a URL, a shell command saved to history, an issue, or CI logs.
* Public redirect: `GET /CODE` returns 302 only after analytics commits. Public stats: `GET /api/v1/urls/CODE/stats` reports retained seven-day events.
* Cache eviction: the DELETE route also requires the private key. Metrics, Prometheus, environment and other diagnostic endpoints return 404 publicly.
* Demo budgets survive Redis/app restarts in PostgreSQL. Daily budgets use UTC. Expired mappings remain canonical records returning 410 and still count toward the 10,000-mapping storage cap; an operator must review capacity when that limit approaches.
* Redis is disposable; canonical mappings and analytics are in PostgreSQL. Its loss falls back to PostgreSQL and a bounded local traffic limit. If PostgreSQL cannot commit analytics, the redirect returns 503 rather than acknowledging an event that can be lost on a crash.

Generate `.env` once; initialization variables do not rotate passwords in an existing PostgreSQL volume. Rotate a database role with a private interactive `psql` password operation, then update the matching private environment variable and recreate only affected containers. Keep the management key and analytics salt independent. Do not regenerate the entire environment on update.

## Final cloud acceptance after capacity returns

Run `verify.py` against the HTTPS hostname with normal certificate verification. From outside the VM verify that only 80/443 and the approved restricted SSH path are reachable; PostgreSQL/Redis/app/metrics ports must be unreachable. Inspect the certificate's issuer and expiry. Reboot the VM in a maintenance window, reconnect, and verify Docker/app/database/HTTPS recovery, preserved mappings, backup restoration and resource use. Recheck OCI inventory/free eligibility and absence of new paid resources. Until those checks are performed on an actual VM, report them as pending.

For local reproducibility on Linux:

```bash
mvn -B verify
docker build -f deploy/oracle/Dockerfile -t shortener:local .
python3 deploy/oracle/validate-local.py --image shortener:local
(cd deploy/oracle/ui-test && npm ci --ignore-scripts && npx playwright install chromium && npm test)
python3 deploy/oracle/exercise-ops.py
python3 deploy/oracle/validate-local.py --stop
```

The Linux shell operations require `flock`; Windows local API/browser validation is supported, while CI runs the actual Linux backup/update/rollback scripts. Local test volumes are intentionally preserved. This test stack is separate from SupplyPrint and cloud resources.
