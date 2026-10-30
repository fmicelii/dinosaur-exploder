# Browser session isolation decision

Status: local two-/three-worker smoke tests, player handoff, ingress busy/retry,
and known management-route blocking passed. Public HTTPS validation and
sustained acceptance remain open.

## Evidence

The current application cannot safely host independent games in one JVM:

- [GameData](../../src/main/java/com/dinosaur/dinosaurexploder/model/GameData.java)
  holds selected ship, weapon, difficulty, and specialty in mutable static fields.
- Cached FXGL 21.1 bytecode, inspected with Java 21 `javap`, declares the
  application, settings, engine, and main window as static fields in
  `com.almasb.fxgl.app.FXGLApplication`.
- [GameConstants](../../src/main/java/com/dinosaur/dinosaurexploder/constants/GameConstants.java)
  specifies relative score, coin, and achievement files. Settings also use
  a relative file. Independent JVMs alone do not isolate files if their working
  directory is shared.
- FXGL shutdown terminates a static executor. Reusing an exited engine in the
  same JVM is not an established safe lifecycle.

This is sufficient to reject shared-JVM independent games without deliberately
corrupting two players' sessions. The alternative pool's runtime evidence is
recorded below; it does not establish production readiness.

## Decision

Use one active browser game per JVM. Prefer JPro's existing load balancer
over implementing a custom allocator and WebSocket proxy.

The [official JPro documentation](https://github.com/JPro-one/JPro-documentation#jpro-loadbalancer)
documents:

```properties
one.jpro.loadbalancer.sessionsPerServer=1
one.jpro.loadbalancer.localServerCount=2
one.jpro.loadbalancer.warmedUpServers=1
one.jpro.loadbalancer.userHome=isolated
```

Two workers are the initial validation pool, not a measured production capacity.
The session is a game/browser instance, not a user account.

Version compatibility was checked against the actual
`jpro-loadbalancer-2025.3.3.jar` from JPro's Maven repository, matching the
project's JPro version. Its packaged properties include the settings above.
Do not copy newer documentation's configuration options without verifying
their presence in this pinned version.

The pinned worker implementation sets `JPRO_WORKING_DIR` to a worker-specific
directory, and `userHome=isolated` recreates that worker's home directory at
process startup. However, the game's saves are relative to its working
directory, not its home. Therefore `userHome=isolated` alone is not proof of
save isolation or fresh progress for the next occupant of a reused worker.

## Required lifecycle

1. A free worker accepts one browser game; an occupied worker cannot initialize
   a second independent FXGL engine.
2. Each worker uses a distinct working directory and home.
3. Before allocating a used slot to another player, the previous game process
   must exit and session save files must be reset in that slot's isolated
   directory. Do not delete desktop saves or shared host directories.
4. A temporary disconnect has an explicitly tested grace period. Expiry ends
   the game and releases its resources. Refresh/reconnect behavior must be
   tested rather than inferred from affinity.
5. When both slots are occupied, an additional player receives an explicit
   capacity response, not an unlimited extra JVM or another player's game.

First verify what the existing load balancer already guarantees. Add only the
missing lifecycle behavior; do not write a second orchestrator preemptively.

## Linux packaging evidence

On 2026-10-29, from the Windows checkout:

```powershell
mvn -B clean package jpro:release '-Djpro.releasePlatforms=linux'
```

Passed: 91 tests, no failures, errors, or skipped tests.
The resulting `target/dinosaur-exploder-jpro.zip` contains:

- `dinosaur-exploder-jpro/bin/start.sh`;
- JavaFX `21.0.7-jpro+2` Linux native jars under `jfx/linux`;
- no packaged local score, coin, achievement, or settings files.

The generated launcher supports `JPRO_WORKING_DIR` and `--working-dir`.
It expects Bash and selects the platform-native JavaFX jars.
This verifies cross-platform packaging, not Linux runtime compatibility.

An initial non-clean build failed with stale compiled classes referencing
`Direction`; a clean build passed without source changes. Use a clean build
for reproducible release validation.

## Runtime acceptance before publishing

- Two real browsers routed to distinct game PIDs and working directories.
- Different ship/difficulty selections do not affect the other game.
- Movement, pause, restart, and disconnect in one game leave the other usable.
- The third independent browser gets the defined capacity response.
- After player A leaves, player C gets fresh progress, not A's files.
- Repeated allocation/disconnect cycles release JVMs and do not accumulate
  retained sessions or unbounded disk usage.
- Measure aggregate JVM memory, including the load balancer, not only heap.
- Child worker ports and management endpoints are not exposed publicly.

## Local Linux runtime

After owner-approved WSL and Docker Desktop installation and a Windows restart,
`docker version` confirmed a Linux/amd64 engine and
`docker run --rm hello-world` passed on 2026-10-29.
The WSL 1 optional-component warning does not block this WSL 2 backend.

The root [Dockerfile](../../Dockerfile) builds the Linux release with tests and
runs it as a non-root user. Its default target remains the single-game runtime;
the explicit `pool` target packages the pinned load balancer and reset hook.
Neither is ready to expose publicly. Commands and limits are recorded in the
[container runbook](container.md).

## Two-worker findings (2026-10-30)

The configured two-player pool actually runs **four JVMs**: the load balancer,
a main/static JPro server on port 8100, and player workers on 8101 and 8102.
Only the front port was published, bound to host loopback.

Two browser tabs received separate JPro instances, PIDs, working directories,
and home directories. Different ships and Easy/Normal selections worked;
using a bomb in A left B's bombs unchanged. Both pause and game-over restart
worked in the prototype. Integrated-browser focus emulation kept rendering
active during automation; this is functional evidence, not a real-world
frame-rate measurement or the final separate-browser/profile acceptance.

The stock pool replaced a disconnected player's JVM but retained its relative
game saves. A subsequent player inherited muted music. Neither
`userHome=isolated` nor `reuseUnzippedFolder=false` fixes this: the latter
cleans extraction at pool startup, not on every occupant change.

The packaged [worker launcher](../../deploy/jpro/worker-start.sh) therefore
removes only `highScore.ser`, `totalCoins.ser`, `achievements.ser`, and
`settings.properties` before each worker JVM starts. It requires an existing
canonical numeric slot beneath the fixed pool directory, rejects symlink and
traversal paths, and aborts on reset failure. Desktop launches are unchanged.
The [shell regression tests](../../deploy/jpro/worker-start-test.sh) verify
reset, unrelated-file preservation, argument forwarding, path guards, and
failure propagation during the pool image build.

With that hook installed, the real-browser handoff passed:

- A saved `soundMuted=true` in slot 1/PID 151; B's slot 2/PID 239 stayed false.
- After A navigated away, one bounded check 35 seconds later found PID 151
  gone, replacement PID 431 in slot 1, and no retained settings file.
- C entered that slot, received unmuted music and fresh score/coins, and
  started Easy with five lives and three bombs.
- B's PID 239 and saved settings remained unchanged. B resumed Normal,
  used a bomb, fired with browser SFX, and paused successfully.

These are observed PIDs and one handoff, not stable identifiers or a measured
disconnect grace period. Twenty-cycle cleanup and 30-minute stability tests
remain required.

### Findings in the direct-pool image

- **Capacity response:** with both player slots occupied, a third tab is
  rejected without creating another game JVM, but sees endless `Reconnecting`.
  The server logs `Currently no server is available!`. Do not label arbitrary
  connection failures as "server full". The local ingress below addresses
  this without changing the allocator.
- **Public management surface:** unauthenticated `/loadbalancer`,
  `/loadbalancer/status`, and `/jpro/api/instances` return HTTP 200 in the pinned
  load balancer. The configured API key does not make these aggregate routes
  private. The local ingress blocks these route families; do not expose the
  pool directly, and retest the eventual public HTTPS ingress.
- **Resource budget:** a diagnostic snapshot with two paused games used about
  1.53 GiB of the 4 GiB container limit. This includes all four JVMs but is not
  peak memory, sustained gameplay capacity, or a hosting recommendation.

## Three-slot follow-up (2026-10-30)

The same built pool image accepted
`--one.jpro.loadbalancer.localServerCount=3` and hosted three active games.
All three HUDs changed during one common observation window, with different
difficulties, separate instance IDs, player PIDs, and slot directories.
The expected five JVMs were present. A bomb in C did not change A/B's counts,
and A/B's game-over restarts left C's process intact.

Sparse samples reached about 2.82 GiB aggregate container memory without OOM
under the same 4 GiB / 4 CPU diagnostic limits. This confirms three players
are possible, not that those limits meet production smoothness or stability
requirements. See the [runbook](container.md) for reproduction and measurement
limitations. The configured default remains two slots until capacity is
deliberately selected. Any slot count needs overflow UX and ingress protection.

## Local ingress follow-up (2026-10-30)

[Compose](../../deploy/compose.yaml) now exposes only the loopback-bound Nginx
ingress, not the pool or workers. JPro still owns allocation, affinity,
concurrency limits, and recycling. There is no second allocator or queue.

The pinned WebSocket capacity rejection closes with code 1011 and an empty
reason, which is not unique to a full pool. The ingress therefore reads the
pinned load balancer's internal status response, validates its counters, and
returns only `{"full":true}` or `{"full":false}`. Missing, inconsistent, failed,
or timed-out telemetry yields logged HTTP 503 with
`{"error":"capacity_unavailable"}`, never an invented capacity answer.

The ingress-only browser entry checks before creating a game. A full pool
opens a static busy page with a manual retry link. A race for the last slot
can still occur: on an initial JPro `reconnecting` state, the page rechecks
capacity and navigates to the busy page only when the status confirms full.
Preflight does not reserve a slot. Once JPro reaches `running`, this monitor
disconnects and leaves that game's existing reconnect behavior alone.

Navigation matters: merely removing the pinned JPro custom element left its
retry timers running. A real navigation stopped additional WebSocket attempts
in a five-second bounded observation after the race test.

The ingress denies the `/loadbalancer` and `/jpro/api` path families, including
app-prefixed variants; ambiguous double-encoded, semicolon, and backslash
paths are rejected before proxying. The internal status subrequest cannot be
called externally. The public capacity endpoint is no-store and rate-limited;
it does not return addresses, instance IDs, player data, or keys.

Local tests passed full-pool/retry, last-slot-race handling, slot release and
subsequent admission, gameplay/audio through the proxy, known management-route
denials, invalid telemetry, a real backend outage, and recovery after restart.
The ingress also starts with the backend absent and resolves its Docker
service name dynamically. This is not a comprehensive security review,
HTTPS test, cross-browser pass, or sustained-capacity result.
