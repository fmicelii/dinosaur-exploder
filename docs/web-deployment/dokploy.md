# Dokploy staging deployment

Status: school hosting approved by the owner on 2026-10-31. The new
`Dinosaur Exploder` project and `browser-staging` Docker Compose service have
been created in Dokploy. Autodeploy is off. No school-server containers have
been started and no public game hostname has been assigned.

## Release prerequisites

- Confirm available server capacity with the administrator. The reported
  machine has 31 GB RAM and about six CPU cores, shared with other apps.
  Its physical region and available allocation remain unverified.
- Keep the zero-additional-cost constraint. The game pool is limited to
  4 GiB / four CPUs, and ingress to 128 MiB / one CPU: 4.125 GiB of combined
  runtime memory limits, below the owner's approximate 5 GB ceiling.
  Image builds use additional resources and are not capped by these service
  limits. Schedule a build with the administrator or use separately built
  images if the shared machine lacks build headroom.
- Confirm JPro runtime licensing before public production/beta release.
  On 2026-10-31 the [official pricing page](https://www.jpro.one/pricing)
  stated that development/staging is free and educational licenses are
  available by application using an educational email. The educational
  Request License link leads to the [contact form](https://www.jpro.one/contact).
  The owner does not yet have a license.
- In that request, describe a noncommercial open-source JavaFX browser game,
  JPro 2025.3.3, two single-session player JVMs plus load-balancer/static JVMs,
  and a 4 GiB game-container limit on a shared school machine. Ask whether
  public access is covered and how licensing counts the host versus container
  memory. Do not infer an exemption from the game's open-source license.
  Do not commit a license, key, or personal application details.
- Complete the required 15-minute two-game stability test and external HTTPS
  smoke checks before sharing a public beta. The original intermittent
  startup fault and incomplete music response remain unresolved.

## Configuration

Select **Docker Compose**, not Docker Stack. Configure the public Git provider:

| Setting | Value |
|---|---|
| Repository | `https://github.com/fmicelii/dinosaur-exploder.git` |
| Branch | `feat/web-deployment` |
| Compose path | `./deploy/compose.dokploy.yaml` |
| Autodeploy | Off |
| Deprecated Isolated Deployment option | Off |
| Domain service | `ingress` only |
| Domain container port | `8080` |
| Domain and internal paths | `/` |
| Strip path | Off |
| TLS | HTTPS with a valid certificate for the selected hostname |

The [staging Compose file](../../deploy/compose.dokploy.yaml) extends the
[local services](../../deploy/compose.yaml), preserving their pinned builds,
save reset, resource limits, tmpfs, non-root ingress, and bounded logs.
It uses `!reset` to remove the local published port; require Docker Compose
2.24.4 or newer and verify that the installed Dokploy preview accepts that
tag and resolves the referenced file before deploying.

Neither service publishes a host port. The pool joins only the
project-scoped `backend` network and has `traefik.enable=false`. Only ingress
joins the existing external `dokploy-network`. Do not attach the pool to
that network or configure a domain for it. Do not enable deprecated isolated
deployment rewriting that attaches every service to an edge network.
Inspect Dokploy's final network/label preview; do not rely solely on the
source YAML. Never change the school's global Traefik configuration.

Dokploy's Environment tab must contain a unique, server-only `POOL_ADMIN_KEY`.
The Compose file explicitly injects it into the pool, not ingress.
`POOL_CAPACITY=2` is the initial cap. Generate the key privately and never
print the complete Compose preview/environment after supplying it.

The ingress health check requests sanitized availability. HTTP 200 for either
free or full means capacity telemetry is responding; it is not proof of
playable game initialization. Failure returns unhealthy after the configured
startup grace and consecutive checks. Docker does not restart a container
merely because it is unhealthy; `unless-stopped` restarts exited processes.
Investigate recurring startup failures rather than increasing timeouts.

## Host setup and external validation

1. Confirm the staging configuration is present on the selected Git branch.
2. Inspect the Compose preview before entering real secrets: no published
   ports, only ingress on the edge network, and pool routing disabled.
3. Confirm the selected hostname routes to this server. Use an administrator-
   approved existing hostname or provider-generated hostname; do not change
   school DNS without explicit approval.
4. Add the HTTPS domain to ingress only. Preserve WebSocket Upgrade headers
   and allow long-lived connections through every proxy in the chain.
5. Deploy manually after checking capacity and licensing for the intended
   environment. Inspect startup and container health without printing keys.
6. From an external connection, verify HTTPS assets, `wss:` game connections,
   movement, shooting, audio, pause/resume, restart, two isolated games,
   overflow/retry, and admission after disconnect.
7. Run the existing [ingress checks](../../deploy/ingress/check.sh) against the
   final HTTPS origin, including their full-pool variant. Retest management
   route denials at the outer proxy; an HTTP landing page alone is insufficient.
8. Run the 15-minute stability gate and record server/browser conditions,
   CPU/memory, OOM events, player worker generations, and media failures.

## Updates and rollback

Keep manual deployment until a separately approved autodeploy stage.
Record the deployed source commit and preserve a known-good revision.
Updates, container restarts, and rollback interrupt active games and clear
session-only progress. There is no session-preserving zero downtime.
To roll back, select the previously verified revision in this service and
redeploy it; recheck HTTPS, capacity, and gameplay. Do not reset the shared
server, remove other projects, or run broad Docker cleanup.

## Validation record

Local Compose rendering passed with Docker Compose 5.6.1:

- `docker compose --file deploy\compose.dokploy.yaml config --format json`:
  passed with a disposable placeholder key; checked zero published ports,
  pool-only backend membership, ingress edge membership, and inherited limits.
- `docker compose --file deploy\compose.yaml config --quiet`: passed;
  the existing local configuration still parses.
- Local staging startup using the previously built images and a disposable
  loopback-port/network override passed. `nginx -t` and
  `sh /etc/nginx/check.sh` passed inside ingress; its new health check
  reached `healthy`. Runtime inspection confirmed the pool joined only the
  backend network, `traefik.enable=false`, and `unless-stopped` policies.
  The local test override was not committed.
- Final local shutdown: ingress exited 0, pool exited 143, both `oom=false`
  and PID 0. The test containers, both test networks, temporary image tags,
  and override file were removed. No school workloads were started.

No Java source changed; the previously built images and Java test results
remain applicable. School deployment, valid public TLS, and the required
15-minute stability result are not yet verified.
