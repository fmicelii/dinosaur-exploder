# Web deployment implementation plan

Requirements and acceptance gates: [Specification](spec.md).

## Working rules

- Work on `feat/jpro-web-deployment`; preserve the old Render branch.
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
- [ ] Inspect the current Maven/JPro build configuration and native requirements.
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
- [ ] Decide from evidence whether the existing embedding supports concurrent
      isolated sessions in one process. Do not assume JPro isolation solves
      FXGL global state.
- [ ] Implement only necessary browser compatibility/isolation changes.
      If process isolation is required, estimate its resource and operational
      cost before choosing an architecture.
- [ ] Use isolated session-only progress initially if durable browser saves
      require new identity/storage infrastructure; leave desktop saves intact.
- [ ] Add targeted regression tests and rerun affected desktop tests.
- [ ] Verify refresh, disconnect, restart, and resource cleanup.

Exit: the specification's isolation requirements pass. Otherwise document
the blocker and reconsider the execution model before hosting work.

## 3. Package the verified game

- [ ] Execute the actual JPro release goal and inspect output and launcher;
      do not assume an archive name, profile, port, or JVM option mechanism.
- [ ] Select a supported Linux runtime image from verified native dependencies.
- [ ] Add a multi-stage Dockerfile and focused build-context exclusions.
- [ ] Verify configuration injection, non-root execution, signal handling,
      readiness, and useful error logs.
- [ ] Set measured resource limits with native-memory headroom.
- [ ] Build and run from a clean source context; verify gameplay and restart.
- [ ] Document commands another contributor can reproduce.

Exit: a reproducible Linux container with the same tested browser behavior.

## 4. Measure feasibility and capacity

- [ ] Define a repeatable active-gameplay scenario and measurement method.
- [ ] Measure local JPro and the container under comparable conditions.
- [ ] Capture input-to-visible latency, client-visible pacing, RTT, CPU,
      container RSS, heap/GC, network traffic, and session counts.
- [ ] Run 1 and 2 active sessions, then increase concurrency to find a safe cap.
- [ ] Run the 30-minute stability and repeated-disconnect cleanup checks.
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
[DinosaurWebAppTest](../../src/test/java/com/dinosaur/dinosaurexploder/DinosaurWebAppTest.java).

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
3. Resolve the process-wide FXGL state constraint before testing or promising
   multi-user hosting. Session affinity does not isolate global engine state.
4. A separate earlier attempt reached `RejectedExecutionException` after
   FXGL's static executor had shut down. Quit reaches FXGL shutdown, but
   the actual initiating action in that interactive attempt was not captured.
   Do not conflate this with the independently reproduced restart thread
   violation or assume Escape from the main menu shuts down FXGL.

The restart thread violation has a code fix and a passing browser replay.
The native startup failure, full gameplay acceptance, session isolation,
and hosting selection remain open.
