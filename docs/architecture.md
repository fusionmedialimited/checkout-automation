# Architecture

## Project shape

Single Gradle project, everything under `src/test` — this is a test automation framework, not an
application, so there is no `src/main`.

```
com.investing.pro
├── config      Config: JVM property -> env var -> checked-in default, fails closed if unset
├── safety      PaymentSafetyGate: the only place that authorizes user creation / purchases
├── context     Scenario-scoped state (TestRunContext, BrowserResources), injected by PicoContainer
├── hooks       Cucumber @Before/@After: browser lifecycle, artifact capture
├── pages       Page objects: locators and raw Playwright calls live here, nowhere else
├── steps       Business-readable Cucumber step definitions; delegate to pages/services
├── runners     JUnit Platform @Suite classes that select which features + tags to run
├── model       Plain data records shared across the above (TestUserRef, SubscriptionRef, ...)
└── support     Small stateless utilities (IdGenerator)
```

Packages named in the original scope but not yet populated (`provisioning`, `services`,
`components`) are intentionally absent rather than stubbed with empty classes — see
"Deliberately unimplemented" below and `docs/checkout-testing.md`.

## Dependency injection and scenario scope

`cucumber-picocontainer` gives every Cucumber scenario its own object graph: any glue class
(step definitions, hooks) that takes another glue-ish class as a constructor argument gets a
fresh instance per scenario, with no scope annotations needed. `BrowserResources` and
`TestRunContext` are the two objects carrying scenario state; both are plain classes with a
no-arg constructor, which is all PicoContainer needs to manage them.

There is no static mutable Playwright state anywhere in the framework: `BrowserResources` opens
a new `Playwright`/`Browser`/`BrowserContext`/`Page` in `@Before` and closes all of them in
`@After`, even if `@Before` partially failed (Cucumber still runs `@After` for a started
scenario). Playwright objects are created and used on the single thread executing that
scenario; parallel execution is disabled (`junit.jupiter.execution.parallel.enabled=false`
in `junit-platform.properties`) and only Chromium is supported for now (see
`BrowserResources.launchBrowser`).

## Configuration precedence

Implemented in `Config` (`src/test/java/.../config/Config.java`):

1. JVM system property, e.g. `-Dqa.baseUrl=...`
2. Environment variable: the key upper-cased with `.` replaced by `_`, e.g. `QA_BASEURL`
3. `src/test/resources/config/default.properties` (checked in)

A key absent from all three throws `ConfigValidationException` rather than silently falling
back to `null` or an empty string — this is deliberate: `qa.paymentMode` and
`qa.realCardAuthorizationRef` have no checked-in default for exactly this reason (see
`docs/payment-safety.md`). The precedence logic itself is unit-tested in
`ConfigPrecedenceTest` against injected lookup functions, not real system properties/env, so it
runs identically offline and in CI.

Application environment (`qa.baseUrl`) and payment mode (`qa.paymentMode`) are separate
settings on purpose: pointing the browser at master QA says nothing about whether the payment
processor behind it is wired to sandbox or live credentials.

## Browser lifecycle and diagnostics

- Fresh `BrowserContext` + `Page` per scenario (`BrowserResources.open()` / `close()`).
- Tracing starts in every scenario (unless `qa.artifactPolicy=never`) and is only *saved* to
  `build/artifacts/traces/<runId>_<scenarioId>.zip` when the artifact policy says to; otherwise
  it's discarded to avoid piling up disk usage on green runs.
- Screenshots go to `build/artifacts/screenshots/<runId>_<scenarioId>.png` under the same
  policy.
- Video recording follows the same policy but has the opposite lifecycle to tracing/screenshots:
  Playwright requires the recording directory to be set at `browser.newContext()` time, before
  the scenario's outcome is known, and only finishes writing the file once that context has
  *closed* — so `BrowserResources` records to a scratch temp directory for the whole scenario,
  and `finishVideo(save, path)` (called from `Hooks` right after `closeContext()`, but before
  `close()` tears down the browser/driver connection the save/discard call still needs) either
  copies it to `build/artifacts/videos/<runId>_<scenarioId>.webm` or deletes it, then always
  removes the scratch directory.
- `runId` reuses `GITHUB_RUN_ID` in CI, else a local timestamp; `scenarioId` is a slug of the
  scenario name plus a random suffix — both exist specifically so artifact and log file names
  never collide across scenarios or runs.
- Public Cucumber report publishing is disabled (`cucumber.publish.enabled=false` in
  `junit-platform.properties`).

## Test suite separation

`./gradlew test` (the default task, no other args) only runs plain JUnit Jupiter classes matching
`**/*Test.class` — see `build.gradle`'s `test { include/exclude }` config. Cucumber suite runners
live under the `runners` package and are named so they never match that pattern (e.g.
`SmokeTestRunner`, not `SmokeTestRunnerTest`); each gets its own dedicated Gradle task (e.g.
`qaSmokeTest`) instead of being reachable through `test` at all — Gradle's file-pattern
`include`/`exclude` and its name-based test filter (`--tests`) are independently enforced (a
class must pass both), so unlike Maven Surefire, an explicit test-name filter cannot override a
task-level `exclude`. This is what keeps "offline" (`./gradlew test`) from ever touching a
browser or the network — see `docs/checkout-testing.md` for the exact commands per suite.

## CI runners

- `ci.yml` (compile + offline `*Test.java` suite, on push/PR) runs on `ubuntu-latest`: it never
  opens a browser or reaches any network target, so a public runner is sufficient and no runner
  authorization is needed.
- `qa-smoke.yml` (manual, loads master QA) runs on `medium`, a self-hosted runner pool shared
  with the reference project `fusionmedialimited/Cucumber-Playwright-POC`. This is required
  because master QA and other InvestingPro QA environments are not publicly reachable — a
  GitHub-hosted runner has no route to them, the same constraint that project documents for its
  own QA-targeting workflows. Both this project's use of that specific pool and the reachability
  constraint itself were confirmed by the project owner (2026-09-11). Using the pool is a
  CI-infrastructure choice only — it creates no runtime dependency on the reference project's
  code, and this project's workflow still runs its own Gradle/Java toolchain independently.
- `qa-smoke.yml`'s job runs inside the official `mcr.microsoft.com/playwright/java` container
  image (the same pattern the reference project uses for its browser-touching jobs), which ships
  Chromium and its OS dependencies preinstalled — no separate `playwright install` step is
  needed. The image tag's version must be bumped in lockstep with `playwrightVersion` in
  `build.gradle`; a mismatch risks the container's browser build drifting from the Playwright
  Java client driving it.
- `qa-smoke.yml` exposes `baseUrl` as a `workflow_dispatch` input, passed through as
  `QA_BASEURL` — `Config`'s existing precedence chain (env var over checked-in default) picks it
  up with no code change. `headless` is deliberately **not** exposed here (always headless in
  CI): the `mcr.microsoft.com/playwright/java` container has no display server (Playwright's own
  Docker docs confirm no Xvfb/noVNC by default), so a headed launch would just fail — headed
  stays a local-only debugging option (see `README.md`). Browser choice is likewise not exposed:
  only Chromium is supported today (`BrowserResources.launchBrowser`), so a variable input would
  just add a way to fail.
- A future workflow that runs authorized sandbox or real-card purchases (see
  `docs/checkout-testing.md` → "Execution paths") needs its own runner-scope review before it is
  created — do not assume `medium`'s current access/secret scope is appropriate for that
  higher-stakes case just because it was confirmed appropriate for the read-only smoke scenario.
- `qa-smoke.yml` retries only previously-failed scenarios, via Cucumber's `rerun:` plugin output
  (`build/cucumber-rerun/smoke.txt`) — never a blanket rerun of the whole suite, which would
  silently redo any already-passed scenario. The rerun file's URIs are read and passed back in
  directly (comma-joined) via `-Dcucumber.features=...`, forwarded into the `qaSmokeTest` task's
  forked JVM via `systemProperties = System.properties`; the `@file` syntax is deliberately not
  used — confirmed empirically that the JUnit Platform engine's `cucumber.features` parameter
  silently ignores it (selecting zero scenarios and reporting a false "success", unlike
  Cucumber's own CLI, which does support that syntax). How many retries to attempt is a
  `workflow_dispatch` choice input (`0`/`1`/`2`, default `0`), so retrying is opt-in per
  invocation, not automatic. Every attempt's test-results/artifacts are snapshotted under
  `build/attempts/attempt-N` before the next attempt can overwrite them, and a run that only
  passed after retrying is flagged in the workflow log as flaky rather than reported as clean —
  see `docs/payment-safety.md` "Ambiguous outcomes and retries" for why this mechanism must never
  be reused for a suite that includes a payment-submitting scenario.
- `qa-smoke.yml` has a Slack-on-failure step gated on a `SLACK_WEBHOOK_URL_QA` secret that does
  not exist yet — the step is a no-op until that secret is deliberately added in repo settings.
  This only keeps the extension point ready; it adds no external dependency or egress today.

## Reporting

- **BDD (`SmokeTestRunner`)**: Allure (`allure-cucumber7-jvm`, a plain `testImplementation`
  dependency — no `io.qameta.allure` Gradle plugin) is the canonical report — it writes step- and
  attachment-level results to `build/allure-results`. `qa-smoke.yml` uploads those raw results as
  a workflow artifact; there is deliberately no generated HTML report in CI — the
  `io.qameta.allure` Gradle plugin's own report-generation task pulls in an Allure 3 CLI via Node
  that failed to run reliably when tried in practice, and the reference project's own local
  report flow doesn't use that Gradle task either (it shells out to the `allure` npm CLI
  directly). Raw results are enough: they're also what would be pushed to the shared Allure
  Portal once that's wired up, since the portal renders the report server-side from raw results,
  not from a pre-rendered file. Cucumber's own `html`/`json` plugins are deliberately **not**
  registered (only `pretty`, for console output during a run) — keeping one BDD report format
  instead of two that could drift out of sync. Scenario screenshots and video are attached as
  actual bytes (`Hooks`), not just a path reference, so they show up directly in Allure's own
  result data.
- **Unit tests** (`*Test.java`) keep plain JUnit XML reports and are deliberately **not** routed
  through Allure (no `allure-junit5`): this suite's `SmokeTestRunner` runs the Cucumber engine
  through a JUnit Platform `@Suite`, and adding `allure-junit5` risks it also instrumenting that
  Suite's own JUnit-Platform-engine node — producing duplicate/conflicting Allure results for the
  same scenario — since the two adapters aren't scoped to run in isolation from each other within
  one JVM. Since unit tests and `SmokeTestRunner` are always invoked as separate Gradle task runs
  today anyway (`ci.yml`'s `test` vs. `qa-smoke.yml`'s `qaSmokeTest`), there's no actual need to
  make them share one Allure setup.
- **Allure Portal**: pushing results to the shared, self-hosted Allure Portal that
  `Cucumber-Playwright-POC` uses was initially deferred because that portal's own docs list
  authentication as "TBD" (no auth on its upload/delete endpoints). The portal's owner clarified
  that it is reachable only from InvestingPro's internal network (no access without VPN) and
  considers that network-level gating sufficient — so this is no longer a blocker in principle,
  and the owner has offered a project/folder to push into. Not yet wired: this still needs the
  exact portal base URL, target project id, and confirmation of whether a secret is actually
  required for the push call (the portal's own docs currently suggest none is). Until those are
  confirmed, results stay CI-artifact-only as described above; wiring the push later is additive,
  not a reason to remove the artifact-only path.

## Reference project

`github.com/fusionmedialimited/Cucumber-Playwright-POC` is private; it was unreachable during
the initial setup phase (no GitHub authentication configured in that session) and this scaffold
was built following standard, widely-used Cucumber-JVM + Playwright + JUnit Platform conventions
instead of reference-specific ones — see git history for that phase's reasoning if needed. Once
`gh` was authenticated with `repo` scope, the reference repo was actually inspected. Findings:

- **No checkout automation exists there to copy from.** A file named
  `src/test/resources/features/pro/Checkout.feature` exists but is misleadingly named — its
  actual `Feature:` is "InvestingPro: Top Ideas", testing the Ideas filter/pagination UI, not
  payment or subscription. The repo's real coverage is WarrenAI, Pro (Ideas/Charts/Auth),
  Equities/ETFs/Instruments, and navigation/registration. Nothing here informs checkout-specific
  conventions; only its general framework conventions do.
- **Different tech stack by design in most respects, though this project has since also moved to
  Gradle** (2026-09-11, once it was confirmed to be the org-wide standard rather than a
  POC-specific choice — see git history for that decision) **for build tooling specifically**.
  What's still deliberately different: Java 17 there vs. Java 21 here; Allure 3 + parallel
  execution by default there, vs. this project's Allure 2 (raw results only, no portal push yet)
  + sequential execution — those were, and remain, explicit requirements for this project, not a
  deviation to reconcile.
- **Same DI mechanism, different shape**: they also use PicoContainer via cucumber-picocontainer
  (`org.picocontainer.annotations.Inject` field injection into a single flat `TestContext`
  grab-bag), vs. this project's constructor injection into two purpose-split classes
  (`TestRunContext`, `BrowserResources`). Browser lifecycle differs more substantially: they keep
  one `Browser`/`Playwright` per thread for a whole run (via `ThreadLocal` singletons under
  `PlaywrightManager`), only creating a fresh `BrowserContext`/`Page` per scenario — an
  amortized-cost tradeoff that makes sense for their parallel-by-default execution. This
  project creates a fresh `Browser` per scenario for maximum isolation, which fits the
  sequential-execution requirement given here and wouldn't scale to their parallel model without
  reconsidering it.
- **Config is looser there**: plain `System.getProperty(key, hardcodedDefault)`, no environment
  variable layer, no fail-closed validation — an unsupported browser value silently falls
  through to Chromium rather than erroring. This project's `Config` (JVM property → env var →
  checked-in default, fails closed) is a deliberate improvement on that pattern, per the original
  task's own instruction to improve weak conventions rather than copy them blindly.
- **A concrete, actionable finding for future page objects**: InvestingPro's frontend serves
  build-time **hashed CSS class names** in production/staging (confirmed by their
  `infrastructure/utilities/pro/ProClassHandler` and `decorators/locators/ProLocatorDecorator`) —
  a DOM node that's semantically `class="foo bar"` actually renders as `class="qwe123 wer234"`.
  They resolve this at runtime via a classname→hash map fetched from a `/pro/classnames.json`
  endpoint served off the staging master branch. **Any future page object targeting the
  InvestingPro checkout UI must not rely on human-readable CSS class selectors** — they will not
  match, or will break on the next build. Prefer role/text-based locators (as `LandingPage`
  already does) or replicate the classname-resolution step if a class-based locator is ever
  unavoidable.
- **Evidence of an internal, undocumented-to-us provisioning/verification/cleanup API**: their
  `UserApiHandler` calls internal `/dev-tools/*` and `/members-admin/*` endpoints (sign-up,
  sign-in, temp-user creation via a mobile API, email-verification-code retrieval, user removal).
  This is not something to copy wholesale into this project — those endpoints were not confirmed
  as approved for this project's use, and per this project's own rules a real API must be
  confirmed and approved before use, not adopted just because a legacy sibling project calls it.
  It is, however, concrete proof that *some* internal mechanism exists; see
  `docs/checkout-testing.md` → "User provisioning" for how this changes that section from "no
  candidate found" to "an unconfirmed candidate exists, needs sign-off."
- **Page objects use a decorator pattern**, not `extends BasePage`: `PageFactory` reflectively
  wraps a page object's `Page` field in a `LocatorDecorator` subclass at construction time,
  adding locator-builder convenience methods. This project keeps the simpler inheritance pattern
  (`BasePage`) for now given its current single-page scope; worth revisiting only if locator
  convenience methods start getting duplicated across many page objects.
- **Heavier reporting pipeline, originally**: Allure 3 (self-hosted Allure Portal, Slack
  notification action, video recording on failure) vs. this project's initial Cucumber HTML/JSON
  + JUnit XML + Playwright tracing. This project has since adopted Allure 2
  (`allure-cucumber7-jvm`, raw results only — see "Reporting" above) for its BDD suite, replacing
  its own Cucumber HTML/JSON entirely, and added video recording under the same
  `qa.artifactPolicy` used for tracing/screenshots (see "Browser lifecycle and diagnostics"
  above), on top of — not instead of — the Playwright tracing (screenshots + DOM snapshots +
  timeline) the reference project's screenshot/video-only capture doesn't have. Pushing to the
  shared Allure Portal is not yet wired (see "Reporting" above for what's still needed), but is
  no longer deferred on security grounds — the portal's internal-network gating was confirmed
  sufficient by its owner.

## Deliberately unimplemented (this phase)

See `docs/checkout-testing.md` for the full list and required contracts. In short: user
provisioning, plan/coupon fixtures, subscription-state lookup, and sandbox/real-payment runners
do not exist yet because no approved API, fixture, or verified sandbox integration was
discovered during this setup phase. Nothing fakes these — the `provisioning` and `services`
packages simply don't exist yet rather than holding stub classes.
