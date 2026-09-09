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
- **Different tech stack by design, not oversight**: Java 17 + Gradle + Allure 3 reporting +
  parallel execution by default, vs. this project's Java 21 + Maven + Cucumber/JUnit reporting +
  sequential execution — the latter set were explicit requirements given for this project, not a
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
- **Heavier reporting pipeline**: Allure 3 (self-hosted Allure Portal, Slack notification action,
  video recording on failure) vs. this project's Cucumber HTML/JSON + JUnit XML + Playwright
  tracing. Not adopted here — this project has no Allure Portal URL/credentials, and the original
  task didn't require it — but worth knowing it's the established pattern elsewhere in the org if
  this project's reporting needs grow.

## Deliberately unimplemented (this phase)

See `docs/checkout-testing.md` for the full list and required contracts. In short: user
provisioning, plan/coupon fixtures, subscription-state lookup, and sandbox/real-payment runners
do not exist yet because no approved API, fixture, or verified sandbox integration was
discovered during this setup phase. Nothing fakes these — the `provisioning` and `services`
packages simply don't exist yet rather than holding stub classes.
