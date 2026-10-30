# Web deployment implementation plan

Requirements and acceptance gates: [Specification](spec.md).

## Working rules

- Work on `feat/web-deployment`; preserve the old Render branch.
- Keep changes focused and independently reviewable. Do not import the old
  deployment wholesale.
- Record exact commands, results, environment, and commit for evidence.
  Keep logs and machine-specific measurements outside committed source unless
  deliberately summarized here.
- Investigate baseline failures before changing code. Do not silently skip
  failing tests or call an untested recipe production-ready.
- Check upstream again before opening a PR; integrate updates deliberately,
  rerun affected checks, and follow the repository's contribution/template rules.

## 1. Establish the current baseline

- [x] Fetch upstream and verify HEAD matches upstream main.
- [x] Create the implementation branch.
- [ ] Inspect the upstream browser-hosting issue and historical Render PR,
      including actual changes and available performance evidence.
- [x] Inspect the current Maven/JPro build configuration and native requirements.
- [x] Run `mvn -B package --file pom.xml`, the existing Maven CI build command.
      It includes tests; do not rerun them separately unless needed.
- [x] Diagnose Java launch tooling and verify the actual JPro HTTP server entry point.
- [ ] Manually exercise the functional smoke test and record baseline failures.

Exit: a reproducible baseline and a list separating application problems
from deployment hypotheses. A failed gate blocks dependent work, not reporting.

## 2. Establish independent browser sessions

- [ ] Trace FXGL lifecycle, global/static state, input handlers, save files,
      settings, achievements, timers, and disconnect cleanup.
- [ ] Exercise two separate browser contexts with different actions/settings.
- [x] Decide from evidence whether the existing embedding supports concurrent
      isolated sessions in one process. Do not assume JPro isolation solves
      FXGL global state.
- [ ] Implement only necessary browser compatibility/isolation changes.
      If process isolation is required, estimate its resource and operational
      cost before choosing an architecture.
- [x] Use isolated session-only progress initially if durable browser saves
      require new identity/storage infrastructure; leave desktop saves intact.
- [x] Add targeted regression tests and rerun affected desktop tests.
- [ ] Verify refresh, disconnect, restart, and resource cleanup.

Exit: the specification's isolation requirements pass. Otherwise document
the blocker and reconsider the execution model before hosting work.

## 3. Package the verified game

- [x] Execute the actual JPro release goal and inspect output and launcher;
      do not assume an archive name, profile, port, or JVM option mechanism.
- [x] Select a supported Linux runtime image from verified native dependencies.
- [x] Add a multi-stage Dockerfile and focused build-context exclusions.
- [ ] Verify configuration injection, non-root execution, signal handling,
      readiness, and useful error logs.
- [ ] Set measured resource limits with native-memory headroom.
- [x] Build and run from a clean source context; verify gameplay and restart.
- [x] Document commands another contributor can reproduce.

Exit: a reproducible Linux container with the same tested browser behavior.

## 4. Measure feasibility and capacity

- [ ] Define a repeatable active-gameplay scenario and measurement method.
- [ ] Measure local JPro and the container under comparable conditions.
- [ ] Capture input-to-visible latency, client-visible pacing, RTT, CPU,
      container RSS, heap/GC, network traffic, and session counts.
- [ ] Run 1 and 2 active sessions, then increase concurrency to find a safe cap.
- [ ] Run the 15-minute stability and repeated-disconnect cleanup checks.
- [ ] Identify the bottleneck before applying targeted tuning.
- [ ] Record pass/fail/unmeasured against every acceptance target.

Exit: feasibility established, a measured capacity cap, and a justified
resource budget. If unacceptable locally, do not expect a host migration
to solve it.

## 5. Deploy staging and validate the network path

- [ ] Compare an appropriately sized always-on VPS and suitable managed hosting
      using capacity, region/RTT, WebSocket behavior, license terms, cost,
      and maintenance burden.
- [ ] Confirm intended audience region and monthly spending ceiling with owner.
- [ ] Obtain approval before provisioning or changing DNS.
- [ ] Configure HTTPS, secure WebSockets, and tested proxy timeout behavior.
      Reuse JPro heartbeat behavior where supported; do not invent a protocol.
- [ ] Enforce the measured active-session cap with an explicit busy response.
- [ ] Run real-browser functional, isolation, stability, and latency checks
      against staging, not merely an HTTP health check.
- [ ] Demonstrate update interruption, failure recovery, and rollback.

Exit: an accessible endpoint meeting the gates with understood operating costs.

## 6. Prepare release and contribution

- [ ] Automate build/packaging checks using existing CI patterns.
- [ ] Add deployment automation only for the selected, validated hosting path.
- [ ] Add the website play link once the game endpoint is stable; run the
      relevant website checks only when that surface changes.
- [ ] Document configuration, limits, monitoring, progress retention, session
      interruption, deployment, and rollback.
- [ ] Reconcile upstream changes and prepare focused PRs with exact evidence.

Suggested contribution boundaries: browser correctness, container packaging,
then hosting/website integration. Adjust if upstream maintainers prefer a
different review structure.

## Evidence and decision record

| Date | Item | Result |
| --- | --- | --- |
| 2026-10-27 | `git fetch upstream --prune` | Passed |
| 2026-10-27 | `git rev-list --left-right --count HEAD...upstream/main` | `0 0`; both at `1723aef54901ea225786645b237e401b2872bdf0` |
| 2026-10-27 | `git switch -c feat/jpro-web-deployment` | Passed |
| 2026-10-27 | Implementation validation | Not run; this change adds planning documents only |
| 2026-10-27 | `mvn -B package --file pom.xml` baseline attempt | Blocked before execution: Maven and Java are not on PATH; no tests ran |
| 2026-10-27 | Bounded local tool discovery | VS Code includes a Java 25 runtime, but no Maven installation or Maven wrapper was found in the checked locations; Java 21 build toolchain still needs provisioning |
| 2026-10-27 | User-local toolchain provisioning | Installed Temurin JDK 21.0.12.1+1 and Maven 3.9.16 after owner approval; official archive checksums verified; no global PATH changes |
| 2026-10-27 | `mvn -B package --file pom.xml` with process-local toolchain | Passed: 87 tests, 0 failures, 0 errors, 0 skipped |
| 2026-10-27 | Java debug-tool launch of `DinosaurWebApp` | Failed before launch: tool reported Maven build exit code 1 without compiler output; cause unconfirmed, no automatic retry or terminal relaunch |
| 2026-10-28 | Debug-tool implementation inspection | Tool hardcodes `mvn compile` using inherited environment; it does not honor the separately provisioned Maven path. Local tools remain usable with process-local environment |
| 2026-10-28 | Explicit local-toolchain VS Code task, `mvn -B compile jpro:run` | Server started on port 8080 and returned HTTP 200 with title Dinosaur Exploder; menus and gameplay HUD rendered. Not a complete gameplay pass |
| 2026-10-28 | Two connected browser sessions | Observed JPro client `scala.MatchError: 3 (of class java.lang.Byte)` and a tab-close disposal assertion. User reported specialty buttons responded visually but did not act; causal connection remains unproven |
| 2026-10-28 | FXGL 21.1 cached bytecode inspection | Embedded launch assigns process-wide application/settings state; engine and main-window state are also static. Independent game isolation is not established |
| 2026-10-28 | Controlled single-session launch, `mvn -B compile jpro:run '-Djpro.openURLOnStartup=false' '-Djpro.port=8081'` | HTTP 200 before browser connected; JVM exited during game initialization with code -1073741819 (`0xC0000005`). Did not reach specialty selection |
| 2026-10-28 | Windows Application Error event 1000 | Faulting PID 20956, module `C:\WINDOWS\System32\RPCRT4.dll`, exception `0xc0000005`; no matching JVM fatal-error file found in checked locations. Native root cause unconfirmed |
| 2026-10-28 | Diagnostic cleanup | Original session-owned server/Maven processes stopped; controlled run exited; removed the temporary local-toolchain VS Code task and stale session-generated PID file |
| 2026-10-28 | Fresh single-browser baseline | Locked specialty displayed its requirements dialog; dismissing it, selecting None, and clicking Next reached difficulty selection and started Easy mode. The reported specialty-button failure did not reproduce |
| 2026-10-28 | Unpatched game-over restart | Reproduced `Initialization failed`, caused by JPro's JavaFX-thread assertion. Stack: FXGL background `InitAppTask` -> `DinosaurApp.initGame` -> entity spawning -> live `GameScene` view mutation |
| 2026-10-28 | Browser-only initialization fix | The web entry point dispatches game and physics initialization to the JavaFX Application Thread and waits for completion; callback failures propagate. Desktop entry point unchanged |
| 2026-10-28 | `mvn -B '-Dtest=DinosaurWebAppTest' test` | Passed: 4 tests for dispatch, completion, inline execution, and failure propagation |
| 2026-10-28 | `mvn -B spotless:check '-DspotlessFiles=src/main/java/com/dinosaur/dinosaurexploder/DinosaurWebApp.java,src/test/java/com/dinosaur/dinosaurexploder/DinosaurWebAppTest.java'` | Passed |
| 2026-10-28 | Patched initial verification attempt | Native startup crash recurred, process exit -1073741819; no usable JVM crash report. This occurred before the changed game-initialization callbacks were exercised |
| 2026-10-28 | Portable ProcDump provisioning | Owner approved license acceptance/local capture. Downloaded official Microsoft executable, verified valid Microsoft Authenticode signature, attached only to the diagnostic game PID |
| 2026-10-28 | Patched browser verification with ProcDump attached | Passed specialty locked-dialog/None/Next flow, first-game start, pause/resume, bomb counter decrement, game over -> Restart -> five-life HUD -> responsive pause. Arrow-left input moved the rendered ship from x=230 to x=206. Native crash did not recur; no unhandled-crash dump was produced |
| 2026-10-28 | `mvn -B package --file pom.xml` after fix | Passed: 91 tests, 0 failures, 0 errors, 0 skipped |
| 2026-10-28 | Final cleanup and `git diff --check` | Passed; game/Maven/ProcDump processes stopped, ports 8080/8081 have no listeners, controlled browser navigated to blank, temporary task/PID file removed |
| 2026-10-29 | Isolation decision | Shared-JVM deployment rejected from application and FXGL static-state evidence; select JPro's single-session JVM pool for runtime validation. See [decision](isolation.md) |
| 2026-10-29 | Pinned JPro load balancer inspection | Version 2025.3.3 artifact exists; packaged properties include single-session JVM limits and isolated home mode. Worker bytecode sets a distinct working directory; sequential save reset still requires verification |
| 2026-10-29 | `mvn -B package jpro:release '-Djpro.releasePlatforms=linux'` | Failed in tests with stale compiled `Direction` references; release not produced by this attempt |
| 2026-10-29 | `mvn -B clean package jpro:release '-Djpro.releasePlatforms=linux'` | Passed: 91 tests, no failures/errors/skips; inspected Linux JavaFX jars and generated launcher in release ZIP; no source workaround or test bypass |
| 2026-10-29 | Linux runtime prerequisite | Docker is not installed; no Linux container runtime is configured. Runtime isolation and Linux acceptance remain unverified |

## Reproducible local launch

With a Java 21 JDK configured as `JAVA_HOME` and Maven on PATH, run from
the repository root in PowerShell:

```powershell
mvn -B compile jpro:run '-Djpro.openURLOnStartup=false' '-Djpro.port=8081'
```

Quote the `-D` arguments in Windows PowerShell to prevent splitting the
property names. Use only one game browser during baseline diagnosis.
The plugin descriptor confirms these properties. Suppressing automatic
browser opening avoids accidentally creating a second game instance.
This command is a verified server launch path, not a stability guarantee.
Use the JPro stop goal after testing and verify the listener is gone;
never kill unrelated Java processes.

## Browser initialization decision

[DinosaurWebApp](../../src/main/java/com/dinosaur/dinosaurexploder/DinosaurWebApp.java)
now specializes the game initialization hooks for JPro. FXGL 21.1 executes
these hooks in a background task; JPro requires mutations of already-observed
scene nodes to run on the JavaFX Application Thread. The initial game can
appear to work, while restarting exposes the violation.

The bridge waits for each hook to finish and propagates errors back to the
FXGL initialization task. It runs inline if already on the JavaFX thread.
FXGL's existing `startAsyncFX(...).await()` helper was inspected but does not
propagate callback failure to the waiting caller, so it was not used here.
Regression coverage lives in
[FxThreadTest](../../src/test/java/com/dinosaur/dinosaurexploder/utils/FxThreadTest.java).

The successful browser sequence is a smoke test under a native debugger,
not a performance measurement, a 30-minute stability pass, or proof that
the separate native startup crash is fixed.

## Current blockers and next action

1. Capture a native crash dump or otherwise isolate the Windows initialization
   crash. The faulting Windows RPC module alone does not identify a JavaFX,
   audio, JPro, or application root cause. Do not prescribe a dependency
   upgrade or disable a feature without evidence. It is intermittent; the
   monitored run did not reproduce it. Debugger attachment may change timing.
2. Finish the functional matrix, including non-None unlocked specialties,
   verified projectile output, audio, resize, and another browser.
   The locked dialog and Next behavior worked in a clean single-session run;
   this does not explain the earlier report involving two connected sessions.
3. Validate the [single-session JVM pool](isolation.md), including worker
   reuse, save-file reset, capacity response, and disconnect cleanup.
   Session affinity does not isolate global engine state.
4. A separate earlier attempt reached `RejectedExecutionException` after
   FXGL's static executor had shut down. Quit reaches FXGL shutdown, but
   the actual initiating action in that interactive attempt was not captured.
   Do not conflate this with the independently reproduced restart thread
   violation or assume Escape from the main menu shuts down FXGL.
5. Local Linux prerequisites were installed with owner approval:
   installed WSL 3.0.3.0 and enabled Virtual Machine Platform using
   `dism.exe /online /enable-feature /featurename:VirtualMachinePlatform /all /norestart`.
   DISM returned 3010 (success; restart required). Installed Docker Desktop
   4.97.0 using
   `winget install --id Docker.DockerDesktop --exact --source winget --override "install --user --backend=wsl-2" --accept-source-agreements --disable-interactivity`.
   The download hash passed and installation completed successfully.
   The owner restarted Windows; `docker version` then confirmed a Linux/amd64
   engine and `docker run --rm hello-world` passed on 2026-10-29.
   No firmware changes were necessary. Game execution and multi-worker
   acceptance remain separate checks.

The restart thread violation has a code fix and a passing browser replay.
The native startup failure, full gameplay acceptance, session isolation,
and hosting selection remain open.

## Linux single-game validation

The [container runbook](container.md) records build/run/cleanup commands and
the real-browser smoke test. Linux execution uncovered native server-side
audio-device initialization, which HTTP readiness alone did not catch.
JPro now uses client-side HTML audio through its existing WebAPI; desktop
continues using JavaFX media. No host audio device or disabled-audio fallback
is used to get a successful game startup.

The original initialization/restart bridge is now shared in
[FxThread](../../src/main/java/com/dinosaur/dinosaurexploder/utils/FxThread.java).
The game reached active gameplay and passed game-over restart in Linux.
The final Linux build passed all 100 tests; the Windows regression suite
passed 99 tests before the last dispatch test, followed by all 13 affected
tests and focused Spotless checks. Client music/effects playback, music
mute, ended-effect cleanup, and page-navigation cleanup were observed.
This does not resolve the historical Windows native crash or complete
multi-worker acceptance.

## Bounded-pool validation (2026-10-30)

The [pool runtime record](isolation.md) and [runbook](container.md) now include
the actual two-worker image, startup/reset hooks, and browser findings.
Two independent game JVMs worked concurrently; a used slot was recycled and
its next occupant received fresh settings/progress while the other game
remained usable. All 100 Linux Java tests and worker-reset shell tests passed.

The test found and fixed retained save files across occupants and expiring
browser audio URL permissions. Static audio playback, firing effects, and
requests after the original permission lifetime passed. Missing/empty pool
administration keys now abort before Java starts.

Next gates, before public deployment:

1. Revalidate the locally passing capacity signal and busy/retry UX through
   the eventual public HTTPS ingress. The direct-pool image retains the
   stock reconnect loop and must not be published directly.
2. Revalidate management/API route blocking at that same public ingress.
   The local Compose stack exposes only Nginx, which blocks known management
   route families. The pool key alone does not protect aggregate routes.
3. Diagnose intermittent worker-startup failures observed during repeated
   lifecycle testing. A fresh-stack uninterrupted 20-play/disconnect retest
   passed without a code/configuration fix; this does not explain the earlier
   failures. Finish separate-browser/profile, refresh/reconnect, repeated
   handoff, shutdown, and bounded-file tests. Repair the stability harness's
   game-over/click handling, investigate the truncated music response, and
   complete the still-open 15-minute two-session stability gate (duration
   revised by the owner on 2026-10-30).
4. Measure sustained gameplay and network behavior, including the main/static
   server. The two-player pool has four JVMs, not two. A roughly 1.53 GiB
   snapshot is not a resource budget. Static MP3 requests currently return
   complete files even when clients request byte ranges.
5. Only then select hosting, confirm region/budget/licensing with the owner,
   and obtain approval before spending money or changing DNS.

Three-player follow-up: the existing image accepted a runtime three-slot
override and passed a short concurrent gameplay test with five JVMs.
All three HUDs progressed in a common observation window; A/B restart and
C-only bomb use preserved separate state. Sparse aggregate memory samples
reached about 2.82 GiB without OOM under diagnostic 4 GiB / 4 CPU limits.
The [runbook](container.md) records commands and caveats. This removes any
ambiguity about a fundamental two-player limit but does not complete the
sustained-concurrency, pacing, or latency acceptance gates.

Ingress follow-up: the [local Compose stack](../../deploy/compose.yaml) now
adds pinned Nginx/njs as a transparent WebSocket proxy and a sanitized capacity
check, not a second allocator. Parser/handler tests, Nginx syntax, live route
guards, and browser full/retry/last-slot-race tests passed. A released slot
admitted a waiting browser without affecting the other worker. Actual backend
outage and restore, invalid capacity responses, and a failed JPro script load
produce explicit unavailable/retry behavior, not a false "server full".
The ingress is non-root/read-only, with dropped capabilities, bounded logs,
and a private backend. Desktop and direct single-game entry points are unchanged.
See the [runbook](container.md) for reproduction and remaining limits.
