# Architecture

## Project shape

Single Maven module, everything under `src/test` — this is a test automation framework, not an
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
  `target/artifacts/traces/<runId>_<scenarioId>.zip` when the artifact policy says to; otherwise
  it's discarded to avoid piling up disk usage on green runs.
- Screenshots go to `target/artifacts/screenshots/<runId>_<scenarioId>.png` under the same
  policy.
- `runId` reuses `GITHUB_RUN_ID` in CI, else a local timestamp; `scenarioId` is a slug of the
  scenario name plus a random suffix — both exist specifically so artifact and log file names
  never collide across scenarios or runs.
- Public Cucumber report publishing is disabled (`cucumber.publish.enabled=false` in
  `junit-platform.properties`).

## Test suite separation

`mvn test` (no arguments) only runs plain JUnit Jupiter classes matching the default
`**/*Test.java` pattern — see `pom.xml`'s surefire configuration. Cucumber suite runners live
under the `runners` package and are named so they never match that pattern (e.g.
`SmokeTestRunner`, not `SmokeTestRunnerTest`); they only run when named explicitly with
`-Dtest=<RunnerClassName>`. This is what keeps "offline" (`mvn test`) from ever touching a
browser or the network — see `docs/checkout-testing.md` for the exact commands per suite.

## Reference project

The task named `github.com/fusionmedialimited/Cucumber-Playwright-POC` as a reference. It
returned HTTP 404 over an unauthenticated fetch, and `git clone` prompted for credentials —
i.e. it is private or does not exist under that name, and this session has no GitHub
authentication configured (no `gh` CLI, no GitHub MCP tool available in this environment). The
org's public repositories were checked and none matches. This scaffold therefore follows
standard, widely-used Cucumber-JVM + Playwright + JUnit Platform conventions instead of
reference-specific ones. If the reference repository becomes reachable, re-run this discovery
and reconcile any conventions that differ (naming, tagging scheme, hook ordering, reporting
setup).

## Deliberately unimplemented (this phase)

See `docs/checkout-testing.md` for the full list and required contracts. In short: user
provisioning, plan/coupon fixtures, subscription-state lookup, and sandbox/real-payment runners
do not exist yet because no approved API, fixture, or verified sandbox integration was
discovered during this setup phase. Nothing fakes these — the `provisioning` and `services`
packages simply don't exist yet rather than holding stub classes.
