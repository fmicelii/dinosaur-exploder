# Local Linux container validation

Status: single-game, local two-/three-worker smoke tests, and local ingress
busy/retry checks passed; not a public multi-player deployment.
See the [session isolation decision](isolation.md) before publishing an endpoint.
For local multi-player use, prefer the [ingress stack](#local-ingress-stack)
over the direct-pool diagnostic command.

## Requirements

- Docker with a working Linux/amd64 engine; Docker Desktop with WSL 2 works
  on Windows. The image has not been validated on ARM.
- Internet access for the initial image, Maven, and Ubuntu package downloads.
- No host Java or Maven installation is needed for the container build.

## Build and run

From the repository root:

```powershell
docker build --progress=plain --tag dinosaur-exploder:linux-validation .
docker run --detach --name dinosaur-linux-validation `
  --publish 127.0.0.1:8081:8080 `
  --memory 2g --cpus 2 `
  --tmpfs /var/lib/dinosaur-exploder:uid=10001,gid=10001,mode=0700 `
  dinosaur-exploder:linux-validation
docker logs --tail 60 dinosaur-linux-validation
```

Open <http://127.0.0.1:8081/> in **one browser tab only**. Wait for the startup
screen and click to start. Browsers can delay audible playback until that
first real click or keypress.

These are diagnostic resource limits, not a measured hosting recommendation.
Do not open a second independent game against this single JVM.

The [Dockerfile](../../Dockerfile):

- builds from source using Java 21 and Maven, including the test suite;
- produces and extracts the JPro Linux release;
- uses Ubuntu 22.04/Temurin 21 with GTK, X11, font, and ALSA libraries;
- runs as UID/GID 10001, not root;
- keeps application binaries separate from writable game data;
- changes the generated launcher's final Java invocation to `exec`, making
  Java PID 1 so Docker's termination signal reaches it directly.

The base images are pinned to the tested digests (Temurin 21.0.13).
Ubuntu packages are resolved at build time. Update image digests deliberately
and rerun validation for security/maintenance updates. Do not treat this
validation image as a finalized deployment recipe.

The tmpfs mount holds relative save/settings files and is cleared on container
stop. Do not bind a desktop save directory here. This establishes disposable
storage for this local container, not lifecycle guarantees for a load-balancer
worker reassigned to another player.

## Browser audio

The initial Linux game attempt returned HTTP 200 but failed while building
the main menu: JavaFX `MediaPlayer` could not open the default audio device.
A headless game server must play audio in the player's browser, not depend
on a server sound card.

[AudioManager](../../src/main/java/com/dinosaur/dinosaurexploder/utils/AudioManager.java)
retains native JavaFX playback on desktop. The JPro entry point instead
enables [BrowserAudio](../../src/main/java/com/dinosaur/dinosaurexploder/utils/BrowserAudio.java),
using the existing pinned WebAPI to control HTML audio elements.
[Maven resources](../../pom.xml) additionally copy the packaged MP3 files
into JPro's static `/audio/` directory. No arbitrary host files are published.

An initial implementation cached `createPublicFile` URLs. The pinned WebAPI
grants temporary publication permissions, and a pool replay produced HTTP 500
"Access ... is forbidden" for music. Durable static resources replace that
mechanism; recurring HTML media requests must not depend on temporary grants.

Music and effects have separate mute/volume controls. Finished effects and
replaced music release their elements and sources. Page navigation stops
all client audio. Autoplay denial waits for a real user gesture and logs
that condition; resource/playback errors are reported in the browser console.
Network-disconnect and worker-reassignment behavior still require testing.

The menus previously constructed extra native media players which they
never played. Those allocations were removed; audible desktop playback
continues through the existing audio manager.

FXGL initializes menus from a background task.
[FxThread](../../src/main/java/com/dinosaur/dinosaurexploder/utils/FxThread.java)
now shares the existing wait-and-propagate bridge between game initialization
and browser audio commands.

## Validation evidence (2026-10-29)

- `docker version`: Linux/amd64 engine responded.
- `docker run --rm hello-world`: passed.
- `docker build --progress=plain --tag dinosaur-exploder:linux-validation .`:
  passed Linux source compilation, all 100 tests (0 failures/errors/skips),
  and JPro release.
- Windows: `mvn -B clean test spotless:check` with a focused `spotlessFiles`
  selector passed all 99 tests before the final dispatch test was added.
  Then `mvn -B test spotless:check -Dtest=BrowserAudioTest,AudioManagerTest,FxThreadTest`
  with the same formatting selector passed all 13 affected tests.
- Real browser: startup, first ship/weapon, default specialty, Easy difficulty,
  active gameplay, firing, pause/resume, and game-over restart passed.
- Bomb HUD decreased from 3 to 2; score/coins advanced.
- Restart restored score 0, bombs 3, lives 5, and level 1.
- Client audio: music and effects had `paused=false`, advancing playback
  times, and no media error. Music mute updated the actual HTML audio element.
  Finished effects were removed. This is browser playback evidence, not
  human listening confirmation or latency measurement.
- Dispatching `pagehide` released the one remaining music element:
  zero audio elements remained, playback was paused, and its source removed.
- Runtime user was `game` (UID 10001); Java was PID 1.
- A controlled stop returned exit 143 (SIGTERM), not 137/SIGKILL.
- JPro logged a response-already-sent/broken-pipe warning when an initial
  music resource request was cancelled during menu initialization. The game
  continued and the active music resource played successfully; do not
  describe this run as warning-free.

Full multi-player, disconnect, 30-minute stability, performance, and remote
hosting acceptance remain open.

## Two-worker pool

This is a local validation target, not a public hosting configuration.
Read the [isolation findings and blockers](isolation.md) first.

```powershell
docker build --target pool --tag dinosaur-exploder:pool-validation .
$env:POOL_ADMIN_KEY=[guid]::NewGuid().ToString('N')
try {
  docker run --detach --init --name dinosaur-pool-validation `
    --publish 127.0.0.1:8081:8080 --memory 4g --cpus 4 `
    --env POOL_ADMIN_KEY `
    --tmpfs /var/lib/dinosaur-exploder:exec,uid=10001,gid=10001,mode=0700 `
    --log-driver local --log-opt max-size=10m --log-opt max-file=3 `
    dinosaur-exploder:pool-validation
} finally {
  Remove-Item Env:\POOL_ADMIN_KEY
}
curl.exe --max-time 10 --silent --output NUL --write-out 'HTTP %{http_code}' http://127.0.0.1:8081/
docker stats --no-stream dinosaur-pool-validation
```

Port 8081 must be free; stop the single-game validation container first.
The `exec` tmpfs flag is necessary because JPro extracts and executes worker
scripts there. `--init` reaps subprocesses, and the worker wrapper `exec`s Java.
Four JVMs are expected for the two-player pool. Do not publish ports
8100-8102. Do not log full worker command lines: JPro includes API keys in
child JVM arguments. Keep Docker access restricted as usual.

The [pool launcher](../../deploy/jpro/pool-start.sh) rejects a missing or empty
administration key before starting Java. Spring reports an unresolved
placeholder without this guard but can leave a failed process alive.
The key is server-side only; it is not a substitute for protecting the
load balancer's public management routes at the eventual ingress.

### Additional validation (2026-10-30)

- `docker build --target pool --tag dinosaur-exploder:pool-validation .`:
  passed clean Linux compilation, all 100 Java tests, release packaging, and
  worker-reset shell regression tests.
- `docker build --target runtime --tag dinosaur-exploder:linux-validation .`:
  passed; reused the same tested build stage.
- Pool startup returned HTTP 200; both tabs rendered independent games.
  One actual occupant handoff passed, with B remaining usable; see
  [observations](isolation.md).
- Static music playback advanced beyond 150 seconds with no media error;
  six real firing effects reached `playing` and `ended`.
- Repeated `/audio/gameBackground.mp3` requests, including Range headers,
  returned HTTP 200 with `audio/mpeg`, not forbidden errors. This server
  ignored Range and returned the complete 14,196,717-byte file each time;
  partial-content behavior and transfer efficiency are not established.
- Cancelled/replaced music requests still produce JPro connection-reset/
  response-already-sent warnings. Successful playback is not a warning-free
  run. The separate temporary-permission forbidden failure was not observed
  with static audio.
- Missing and explicitly empty `POOL_ADMIN_KEY` launches each exited 1
  immediately with a clear error, without starting game workers.
- The final keyed launcher passed browser menu/audio startup. The default
  single-game image was also replayed through ship, weapon, default specialty,
  Easy gameplay, and pause with active browser music.
- Controlled pool and single-game stops both exited 143, with `oom=false`
  and Docker reporting no running container process. Test tabs were blanked
  and the named validation containers removed afterwards.
- `git diff --check` and `mvn -B spotless:check` with the nine touched Java
  class/test names selected by `spotlessFiles` passed.
- Third-tab capacity failed UX acceptance: `Reconnecting` instead of a
  clear busy response. Management routes also need ingress protection.
  These are direct-pool findings; the ingress follow-up below addresses
  the local entry experience and known management paths.

These 4 GiB / 4 CPU limits are diagnostic, not measured production sizing.
The command above also bounds Docker log retention; it does not establish
bounded application files or sustained lifecycle stability.

### Three-player smoke test

The two-slot default is a validation setting, not a game limit. The pool
launcher forwards Spring command-line overrides. To repeat the three-slot
test, use the same command above with a distinct container name and append
this argument **after the image name**:

```powershell
dinosaur-exploder:pool-validation --one.jpro.loadbalancer.localServerCount=3
```

Keep the host port free and the same private binding and diagnostic limits.
The checked-in configuration still defaults to two slots; no image rebuild
is needed for this override.

On 2026-10-30, `dinosaur-three-player-validation` passed HTTP readiness and
three-tab gameplay with Easy/Normal/Easy selections. Five JVMs were observed:
load balancer PID 7, main/static server PID 53, and player PIDs 151, 239, 240,
with separate slot directories 1, 2, 3. All three live HUDs changed during
the same five-second observation: A's lives 4 to 1, B's lives 3 to 2, and
C's score 3 to 5, coins 1 to 2, lives 4 to 2. C used a bomb (2 remaining)
while the restarted A and B games each retained 3. A and B also completed
game-over restart without replacing C's worker.

This was a short smoke test with integrated-tab focus emulation, not three
human players in separate browser profiles, sustained load, or measured
latency/frame pacing. An initial weapon-click automation race was resolved
by waiting for the weapon image layout; it was not a server launch failure.

Docker samples during the mixed gameplay/game-over run showed 2.692 to
2.816 GiB memory and 17.66% to 154.67% CPU. Docker's CPU scale uses 100% per
logical CPU, not per entire four-CPU allocation. These are sparse samples,
not peak or average consumption attributable exclusively to three active
games. The container remained running with `oom=false`; no known
out-of-memory, forbidden-audio, MatchError, or rejected-executor errors
matched the focused log check. This does not claim warning-free logs.
After blanking the three tabs, the test container stopped with exit 143,
`oom=false`, and process ID 0, and was removed. Use that test's actual name,
`dinosaur-three-player-validation`, in the cleanup commands below.

Navigate every test tab to `about:blank`, then stop this specific container:

```powershell
docker stop --timeout 20 dinosaur-pool-validation
docker inspect --format 'status={{.State.Status}} exit={{.State.ExitCode}} oom={{.State.OOMKilled}}' dinosaur-pool-validation
docker rm dinosaur-pool-validation
```

## Stop and clean up

Close the game tab or navigate it to `about:blank` to stop client audio, then:

```powershell
docker stop --timeout 15 dinosaur-linux-validation
docker inspect --format 'status={{.State.Status}} exit={{.State.ExitCode}} oom={{.State.OOMKilled}}' dinosaur-linux-validation
docker rm dinosaur-linux-validation
```

Do not stop unrelated containers or Windows/WSL services.

## Local ingress stack

The [Compose file](../../deploy/compose.yaml) builds two services:

- `pool`: the existing game/JPro load-balancer image, with no published ports.
- `ingress`: pinned Nginx 1.30.6 with its packaged njs module. This is the only
  published service, at <http://127.0.0.1:8081/>.

This adds a standard reverse proxy and a small telemetry adapter, not game
allocation logic. Capacity uses the pinned JPro 2025.3.3 status counters;
upgrades require rerunning the parser/handler and browser race tests.
There is no queue or slot reservation. JPro enforces the actual player cap.

From the repository root in PowerShell, with port 8081 free:

```powershell
$env:POOL_ADMIN_KEY=[guid]::NewGuid().ToString('N')
$env:POOL_CAPACITY='2'  # Use 3 to repeat the three-player smoke test.
docker compose --project-name dinosaur-local --file deploy\compose.yaml up --detach --build
curl.exe --max-time 10 --silent --include http://127.0.0.1:8081/_pool/availability
docker compose --project-name dinosaur-local --file deploy\compose.yaml exec -T ingress nginx -t
docker compose --project-name dinosaur-local --file deploy\compose.yaml exec -T ingress sh /etc/nginx/check.sh
```

Keep these variables only in this PowerShell process while managing the stack;
do not save/commit an actual key. Do not print `docker compose config` or
complete container environments/command lines, which can include the key.
Compose deliberately rejects a missing/empty key. A live Docker CLI must be
on PATH; use its full local executable path if needed.

Startup may briefly return capacity HTTP 503 while JPro starts. The landing
page provides a manual retry rather than pretending the backend is ready.
`{"full":false}` means the sampled pool was not full, not a reserved slot or
a full gameplay-readiness guarantee. With all slots occupied, the same
endpoint returns `{"full":true}`; pass `true` as the second argument to
the shell check in that state:

```powershell
docker compose --project-name dinosaur-local --file deploy\compose.yaml exec -T ingress sh /etc/nginx/check.sh http://127.0.0.1:8080 true
```

The ingress serves its own landing, busy, and unavailable pages. It fetches
JPro assets only after the capacity check so backend failure cannot stall
the error page behind an unavailable deferred script. Capacity requests have
a five-second browser timeout; client-script loading has a ten-second timeout.
These standalone pages are English, matching the existing browser entry;
in-game language selection is unchanged.

A full initial connection race triggers a second status check and a real
navigation to the busy page. Removing a JPro element alone did not stop its
reconnect timers. Already-running games keep JPro's normal reconnect behavior;
this feature does not establish refresh/disconnect grace semantics.

The ingress runs as `nginx`, on port 8080, with read-only root filesystem,
only a temporary writable directory, all Linux capabilities dropped, and
`no-new-privileges`. It uses Docker's internal DNS resolver to tolerate the
backend being absent or recreated. Its 128 MiB / 1 CPU limit is diagnostic;
pool limits remain 4 GiB / 4 CPUs. Docker logs are bounded for both services.
Only HTTP on loopback is configured: TLS, public hostname, final proxy chain,
licensing, and hosting approval remain separate work.

### Ingress evidence (2026-10-30)

No Java source changed in this follow-up, so the existing 100-test Java build
was reused rather than rerun. Validation used project name
`dinosaur-ingress-validation`.

- `docker compose --project-name dinosaur-ingress-validation --file deploy\compose.yaml up --detach --build`:
  passed both image builds and local startup after fixing read-only temp paths.
- `docker build --file deploy\ingress\Dockerfile --tag dinosaur-exploder:ingress-validation .`:
  passed capacity parser and handler tests, including malformed/inconsistent
  counters, unexpected upstream responses, and rejected network requests.
- `docker exec dinosaur-ingress-validation-ingress-1 nginx -t`: passed.
- `docker exec dinosaur-ingress-validation-ingress-1 sh /etc/nginx/check.sh`
  and the full-pool variant with `http://127.0.0.1:8080 true`: passed.
  These check exact capacity shape, known management-route denials, encoded/
  ambiguous paths, and public game assets.
- Browser: two games rendered through the proxy; ship/weapon/default specialty,
  Easy start, bomb decrement, firing, pause, and browser music passed.
- Third tab: a real full pool displayed the busy page with no game element.
  Retry while full stayed busy; after A left and its worker was recycled,
  retry admitted C while B retained its PID and remained connected.
- Last-slot race: the test deliberately supplied one stale `full:false`
  preflight response against a truly full backend. Reconnect prompted a real
  capacity recheck and busy navigation; the WebSocket-attempt count remained
  unchanged during a subsequent five-second observation.
- Malformed JSON, a nonboolean `full`, and capacity HTTP 503 displayed
  unavailable rather than busy. Simulated JPro-script HTTP 503 also displayed
  unavailable; retry after removing the failure reached a running game.
- Actual pool stop: capacity returned HTTP 503 with
  `{"error":"capacity_unavailable"}` and the browser displayed unavailable.
  Ingress also started while the pool was absent; after restarting the same
  pool container, capacity recovered to HTTP 200 without restarting Nginx.
- `docker port` showed no host port on the pool; only ingress
  `127.0.0.1:8081` was published. Runtime inspection confirmed non-root,
  read-only ingress and `CapDrop=["ALL"]`.
- A diagnostic snapshot with two menu sessions showed about 13.74 MiB
  ingress memory. This is not peak overhead or sustained capacity evidence.
- Final shutdown: ingress exited 0, pool exited 143, both `oom=false` and
  process ID 0. The project's containers and Compose network were removed,
  all shared test tabs were blanked, and `git diff --check` passed.

### Stop the Compose stack

Navigate every game tab to `about:blank` first, then in the same PowerShell:

```powershell
docker compose --project-name dinosaur-local --file deploy\compose.yaml down --timeout 20
Remove-Item Env:\POOL_ADMIN_KEY
Remove-Item Env:\POOL_CAPACITY
```

If reopening PowerShell only to stop the stack, set a temporary non-empty
`POOL_ADMIN_KEY` for Compose's required-variable validation, run `down`,
then remove it. Do not use a new key with `up` on an existing live stack
unless intentionally replacing/restarting the pool.
Do not tear down other Compose projects or run broad Docker cleanup commands.
