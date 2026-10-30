# Browser deployment specification

## Goal and baseline

Make Dinosaur Exploder playable through a public HTTPS URL without installing
Java on the player's device, while preserving the desktop application.

Planning baseline verified on 2026-10-27:

- Upstream: `jvondermarck/dinosaur-exploder`, default branch `main`.
- After fetching upstream, local HEAD and upstream main both pointed to
  `1723aef54901ea225786645b237e401b2872bdf0`.
- Work branch: `feat/web-deployment` (replaced the original planning branch).
- The [Maven build](../../pom.xml) targets Java 21, includes Kotlin compilation,
  FXGL 21.1, and JPro 2025.3.3.
- The existing [browser entry point](../../src/main/java/com/dinosaur/dinosaurexploder/DinosaurWebApp.java)
  embeds the game through JPro.
- The [README](../../README.md) links to an existing Vercel-hosted website.

This is a verified source baseline, not a claim that the current application
builds, supports independent concurrent players, or performs adequately.

## Scope and initial decisions

1. Reuse JPro first. Validate feasibility before investing in hosting automation.
2. Leave the website on its existing hosting. Add a play link or integration
   only after a stable game endpoint exists.
3. Package the game in a reproducible Linux container. Use a bounded pool
   of single-session JVMs behind JPro's load balancer, subject to the
   [isolation validation](isolation.md). Begin with two workers for testing,
   not an unmeasured production capacity promise.
4. Prefer an always-on host near the initial audience for the first staging
   deployment. Select provider and size from measured resource requirements.
5. Do not require Dokploy. Add orchestration only if it reduces demonstrated
   operational work.
6. Preserve desktop behavior and avoid unrelated refactors.

These are implementation choices, not proof that JPro will meet the goal.
If it cannot isolate players or meet the performance gates, stop deployment
work and document the limitation and a scoped alternative before expanding
into an engine rewrite.

## Functional requirements

- A player can load, start, play, lose, and restart from a desktop browser.
- Keyboard controls, menus, pause/resume, resizing, and audio work, or a specific
  browser limitation is documented and accepted before release.
- Two independent browser contexts can play without sharing input, active game
  state, or private player progress.
- Audit shared static state, FXGL lifecycle, file persistence, and cleanup;
  separate scene graphs alone do not establish session isolation.
- Define and test refresh, disconnect, reconnect, idle, and restart behavior.
  Do not imply that reconnect automatically restores an in-memory game.
- Do not publish a multi-user endpoint with accidentally shared save files.
  Persistent per-player storage is not required for the first release:
  use isolated session-only progress if durable identity/storage would
  otherwise expand the scope, and disclose that behavior.
- Desktop saves must retain their existing semantics.
- A fresh checkout can produce and run the same release artifact using
  documented commands and pinned runtime/tool versions.

## Initial validation gates

The following are project targets, not measured results or platform guarantees.
Record hardware, region, browser, viewport, build commit, resource limits,
and network conditions for every performance result.

| Area | Initial acceptance target |
| --- | --- |
| Browsers | Current stable Chrome and Firefox, desktop keyboard controls |
| Functional smoke test | Start, move, shoot, pause, resume, game over, restart, resize, audio |
| Isolation | Two independent browser contexts with no state/input/progress leakage |
| Warm load | Playable within 10 seconds on a defined warm-server test connection |
| Interaction | p95 input-to-visible-response at most 150 ms with measured RTT at most 50 ms |
| Pacing | Target 60 updates/second; record client-visible frame intervals and stalls over 100 ms during active play |
| Stability | Two concurrent real game sessions for 30 minutes without unexplained disconnects, OOM, or crashes |
| Cleanup | After 20 connect/play/disconnect cycles, no increasing retained-session count; inspect post-GC memory for retained growth |
| Routing | HTTPS assets and secure WebSockets work without mixed-content failures |
| Operations | Restart, update interruption, and rollback behavior demonstrated and documented |

Use actual rendered gameplay for latency and pacing measurements; WebSocket
ping time, JavaFX pulse rate, or idle connections are not substitutes.
If client-visible timing cannot initially be instrumented, report it as
unmeasured rather than passing the gate from subjective playtesting.
Treat two players as a feasibility floor, not a production capacity claim.

## Operations and boundaries

- Never commit credentials or actual player save data.
- Verify JPro licensing and redistribution/hosting requirements.
- Set a tested container memory limit and leave room for native JVM memory,
  threads, JavaFX, and other host services; heap size is not total RSS.
- Begin with JVM defaults and measurements; change GC or heap settings only
  to address observed behavior.
- Distinguish readiness from liveness and establish resource cleanup.
- Before public access, cap active sessions at measured safe capacity with
  a clear busy response; avoid unlimited creation of expensive game sessions.
- An update may interrupt active games in the first release. Document it.
  Availability for new connections is not session-preserving zero downtime.
- No paid provisioning, DNS changes, publishing, or deployment credentials
  without owner approval. Audience location and spending ceiling must be
  confirmed before choosing a paid host.

## Out of scope

- Moving the website, adding Kubernetes, or requiring a self-hosted PaaS.
- Accounts, a leaderboard service, or a new multiplayer game mode.
- A JavaScript/WebAssembly engine rewrite without a failed feasibility gate
  and a separate scope decision.
- Multi-host horizontal scaling and session-preserving deployments.

## Completion

The work is complete only when a public browser endpoint meets the agreed
gates, its deployment is reproducible, and operating/rollback instructions
are verified. A locally working container alone is not completion.

Execution order and evidence tracking: [Implementation plan](plan.md).
