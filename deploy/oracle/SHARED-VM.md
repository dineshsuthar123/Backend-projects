# Shared Oracle VM ownership and migration plan

## One provisioning owner

SupplyPrint's existing `deploy/oracle/oci-infra.py` remains the sole VM provisioning owner. The URL shortener does not contain an OCI `launch_instance` call, create another VCN, or copy SupplyPrint's saved state. `resume.py --launch` delegates one **retry-launch** to that existing owner, on its existing network. The owner's instance name may remain `supplyprint-always-free`; applications need not own the host name.

Live inventory must precede every retry. The owner already enforces **tenancy-wide totals** of 2 A1 OCPUs, 12 GB RAM and 200 GB boot/block storage. An existing full-size allocation makes a later new allocation fail. Its retry also rejects saved VM resources and uncertain `VM_LAUNCH_PENDING` outcomes. The owner's 10 cost-guard unit tests passed unchanged during this task. Large quota numbers are never interpreted as additional Always Free capacity.

No SupplyPrint saved-state file, network rule, deployment script, key, capacity watcher or scheduled-task definition has been changed. Its read-only `SupplyPrint-Oracle-A1-Capacity` task remains the capacity monitor. Do not run multiple provisioning retries concurrently, delete its state to bypass a guard, or change the home region/account tier. Following a timeout, inspect inventory and the owner's state before another attempt.

## Host budget before co-hosting

| Component | Memory hard cap | Initial CPU cap | Reviewed shared CPU cap |
| --- | ---: | ---: | ---: |
| URL shortener JVM (384 MiB heap, capped metaspace/direct/code cache) | 1,024 MiB | 0.75 | 0.6 |
| URL shortener PostgreSQL | 512 MiB | 0.25 | 0.2 |
| URL shortener Redis (64 MiB data limit) | 128 MiB | 0.10 | 0.1 |
| One shared Caddy | 128 MiB | 0.15 | 0.1 |
| SupplyPrint JVM **including native ONNX inference** | 1,536 MiB | 1.0 | 0.7 |
| SupplyPrint PostgreSQL/pgvector | 768 MiB | 0.5 | 0.3 |
| **Combined containers** | **4,096 MiB** | **2.75** | **2.0** |

Reserve at least another 2 GiB for Ubuntu/Docker and 1 GiB for filesystem/cache/maintenance. Approximately 5 GiB remains in a 12 GiB host budget, but native inference peaks and concurrent latency still need measurement on the actual host. The original two application CPU budgets together exceed two OCPUs; do not co-host without applying and testing the reviewed lower caps. Do not add simultaneous benchmark loads or promise production throughput from this budget.

The shortener's local x86 acceptance sample used roughly 327–345 MiB for the JVM, 35 MiB for PostgreSQL, 5 MiB Redis and 11 MiB Caddy. These are light local samples, not Oracle peak-memory measurements or evidence that a 1 GiB E2 VM has safe headroom.

## Safe migration sequence before adding SupplyPrint

1. Launch through the single existing owner, retaining its network and narrow SSH source. Record the shared VM reference in private state, without changing the owner resource inventory.
2. Deploy the shortener and the separate `portfolio-proxy` Compose project first. The external network is `portfolio-edge`, subnet `172.30.10.0/24`; Caddy owns `.2`, shortener `.3`, reserved SupplyPrint `.4`. Confirm this subnet does not conflict with existing Docker networks.
3. Preserve SupplyPrint's database volume and its separate admin/migration/runtime credentials. Back it up and restore into an isolated database before a shared-host cutover.
4. Review `supplyprint-shared.override.yml` against SupplyPrint's current Compose: its service is named **proxy**, not caddy. The override disables that standalone proxy, removes the API loopback publish, connects `supplyprint-api` to the shared edge, and adjusts CPU caps. Data remains on SupplyPrint's own internal database network and project-specific volume.
5. Update SupplyPrint's **deployment/operations entry points together** so they always include that reviewed override. Verify a future deploy does not restart its standalone proxy. Do not silently apply only an override while leaving automation on the old command. This step is intentionally gated until a real shared host exists and the owner migration can be tested end to end.
6. Add a separately named Caddy site file for SupplyPrint's confirmed hostname, forwarding to `supplyprint-api:8080`. Review SupplyPrint's trusted-proxy setting for `.2` and retain its own security headers, authentication, upload limits and health routing. Never replace the existing shortener site file or Caddy data volume.
7. Add `compose.shared-budget.yml` to shortener operations and `compose.proxy.shared-budget.yml` to proxy operations consistently; validate both applications under controlled concurrent traffic plus real ONNX inference before declaring co-hosting successful.
8. Rollback is per application image. If the proxy migration fails, stop only the new SupplyPrint API deployment and restore its previous proxy/operations configuration in a maintenance window. Keep both database volumes and all credentials unchanged.

This is a reviewed migration package, not a claim that two applications are already co-hosted. A later ordinary SupplyPrint VM retry cannot claim a second full A1 VM after the shared allocation is visible; its existing allocation guard rejects it.

## E2 Micro evaluation

Oracle documents 1 GiB RAM and a 1/8 OCPU baseline for E2 Micro. The configured complete shortener stack has 1,792 MiB of container caps before Ubuntu/Docker. A light idle sample is insufficient evidence for database durability, migration/restart peaks, backup headroom and sustained latency within 1 GiB. No complete 1-GiB qualification or E2 approval has been obtained; **E2 is not selected or provisioned**. Its live Hyderabad capacity report was also OUT_OF_HOST_CAPACITY. Keep the approved A1 plan.
