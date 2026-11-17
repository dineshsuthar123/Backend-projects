# Deployment status

**Not publicly deployed: Oracle host capacity is unavailable.** No public frontend/API URL or production HTTPS certificate exists yet. No VM, boot volume, paid service, account upgrade, purchased domain or temporary tunnel was created by this task.

## Verified infrastructure

Authenticated inventory refreshed on 2026-11-17, 23:55 IST (18:25 UTC): home region **ap-hyderabad-1**, no compute instances, no boot/block volumes, zero A1 allocation. SupplyPrint's one saved VCN and public subnet match live resources. Both **VM.Standard.A1.Flex 2 OCPUs / 12 GB** and **VM.Standard.E2.1.Micro** report **OUT_OF_HOST_CAPACITY** in Hyderabad's available domain. Official Ubuntu 24.04 images are compatible with both shapes. The existing network allows TCP 80/443 publicly and TCP 22 only from its saved operator /32; it exposes no data-service ports. The capacity watcher remains Ready.

The approved eventual allocation remains A1 **2 OCPUs / 12 GB / 50 GB boot** within the user's combined **200 GB storage ceiling**. The account's Free Tier/non-PAYG status is confirmed by the user; IAM does not supply a billing-plan verification. A1 billing type is LIMITED_FREE, E2 is ALWAYS_FREE. Quota headroom does not prove free allowance.

Oracle's [Always Free documentation](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm) describes the 2-OCPU/12-GB A1 allowance, home-region requirement, combined storage ceiling and possible idle reclamation. The target design incurs **₹0/month of hosting charges while it remains within verified Always Free allowances**. There is no uptime or future host-capacity guarantee.

## Prepared runtime

Planned production containers: `shortener-app`, `shortener-db`, `shortener-redis`, and the separately managed shared `caddy`. Public HTTPS, frontend, API, redirects, aliases, TTL and stats share one hostname. General creation/cache operations require a private key; the browser has a restricted destination/TTL/quota demo. Database data and Caddy certificates use separate persistent volumes. Internal data services have no published ports.

Local verification has passed 74 Java tests with no skips, 10 full-stack acceptance groups and two Chromium browser tests. This includes real PostgreSQL/Flyway, Redis Lua limits, proxy spoof resistance, durable analytics, alias length, TTL, cache outage fallback, container restart persistence and isolated backup restore. ARM64 image build and its Java 21 JVM have run under local emulation. Linux operational rollback and GitHub CI evidence are recorded below as they complete; local results are not Oracle performance measurements.

Dependency-patched Java verification also passes all 74 tests. The scanned Java SBOM contains 105 components; the gate reports no applicable HIGH/CRITICAL finding, with only the two exact, expiring Spring applicability statements described in AUDIT.md. The pinned Ubuntu runtime OS scan contains a real OS result and no HIGH/CRITICAL finding. These scans are time-specific evidence, not a promise that future vulnerabilities will not be disclosed.

[GitHub CI run 44147267327](https://github.com/dineshsuthar123/Backend-projects/actions/runs/44147267327) passed for implementation commit `7aa41ae`: all 74 tests, the complete stack, two browser tests, the **actual Linux backup/restore/update/explicit rollback and automatic failed-release recovery scripts**, ARM64 build/JVM, and both security gates. No paid artifacts or deployment credentials were used. The [branch's latest verification runs](https://github.com/dineshsuthar123/Backend-projects/actions?query=branch%3Adeploy%2Foracle-always-free) record subsequent build/documentation refinements.

## Exact blocked step and retry

The next infrastructure step is the existing owner's **single A1 VM launch**, not network creation. Use the read-only `capacity.py` / `resume.py` commands in [OPERATIONS.md](OPERATIONS.md). When 2/12 capacity is AVAILABLE, `resume.py --launch --supplyprint PATH` delegates one guarded retry to SupplyPrint's saved network. Keep `SupplyPrint-Oracle-A1-Capacity` read-only and unchanged.

After launch, configure a confirmed hostname's DNS and run bootstrap, deployment and public acceptance. Production HTTPS, external port scans, VM reboot recovery, native Oracle resource measurements, final cost/eligibility evidence, and SupplyPrint combined inference/load testing remain pending. E2 has neither a complete 1-GiB qualification nor user approval and is not selected.

Deployment, update, restart, health, backup, isolated restore and rollback commands are in [OPERATIONS.md](OPERATIONS.md). Security findings and scoped dependency applicability statements are in [AUDIT.md](AUDIT.md). Shared host ownership/budgets and the gated SupplyPrint migration are in [SHARED-VM.md](SHARED-VM.md).
