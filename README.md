# InvestingPro Checkout Automation

Java + Playwright + Cucumber automation framework for the InvestingPro checkout journey
(master QA: `https://master--www.ams-qa.finboxgcp.investing.com/pro/`).

This is a **scaffold**: it establishes structure, configuration, safety gates, browser
lifecycle, and one smoke scenario. It does not yet implement checkout, user provisioning, or
payment flows — see `docs/checkout-testing.md` for exactly what's built vs. still required.

## Requirements

- JDK 21 (any distribution). The Gradle wrapper pins Gradle 8.14.5 for you.
- No other local install is required to run offline tests. Running the QA smoke suite also
  needs Chromium, installed via Playwright's own installer (see below) — Playwright will tell
  you if it's missing rather than failing silently. CI runs `qaSmokeTest` inside the official
  `mcr.microsoft.com/playwright/java` container image instead, which ships Chromium
  preinstalled — see `docs/architecture.md` "CI runners".

## Commands

```bash
# Offline: config/safety-gate unit tests only. No browser, no network target.
./gradlew test

# One-time (or after upgrading the Playwright dependency): install the Chromium build
# Playwright's Java bindings expect. Local dev only — CI's qa-smoke.yml runs inside a container
# image that ships Chromium already, so it doesn't need this task.
./gradlew installChromium

# QA smoke: loads the master QA landing page only. No user creation, no payment.
./gradlew qaSmokeTest
```

There is currently no command for QA sandbox checkout or authorized real-payment runs — those
runner classes (and their own Gradle tasks) don't exist yet (see `docs/checkout-testing.md`).

## Project layout

```
.
├── build.gradle, settings.gradle, gradlew, gradlew.bat, gradle/wrapper/
├── .env.example              example env vars (not auto-loaded — see the file header)
├── CLAUDE.md                 project instructions for Claude Code
├── .claude/agents/           role definitions for framework/discovery/provisioning/review agents
├── .github/workflows/        ci.yml (PR/push, offline only), qa-smoke.yml (manual)
├── docs/
│   ├── architecture.md       structure, DI, browser lifecycle, config precedence
│   ├── checkout-testing.md   scope, coverage matrix, execution paths, missing integrations
│   ├── payment-safety.md     env/payment-mode separation, safety gates, secret handling
│   └── mcp-setup.md          MCP/agent tooling used during development (not at test runtime)
└── src/test/
    ├── java/com/investing/pro/
    │   ├── config/      Config, PaymentMode, ArtifactPolicy, ConfigValidationException
    │   ├── safety/      PaymentSafetyGate, SafetyViolationException
    │   ├── context/     TestRunContext, BrowserResources (PicoContainer-injected, scenario-scoped)
    │   ├── hooks/       Hooks (browser lifecycle + artifact capture)
    │   ├── pages/       BasePage, LandingPage
    │   ├── steps/       SmokeSteps
    │   ├── runners/     SmokeTestRunner
    │   ├── model/       TestUserRef, SubscriptionRef, CleanupStatus
    │   └── support/     IdGenerator
    └── resources/
        ├── features/smoke/landing_page.feature
        ├── config/default.properties
        ├── junit-platform.properties
        ├── allure.properties
        └── logback-test.xml
```

`provisioning`, `services`, and `components` packages named in the original scope are not
present yet — see "Explicitly not implemented" in `docs/checkout-testing.md` for why and what
their contracts should look like.

## Configuration

Precedence: JVM property (`-Dqa.baseUrl=...`) → environment variable (`QA_BASEURL`) → checked-in
default in `src/test/resources/config/default.properties`. Full details, including which keys
have no safe default and therefore fail closed, are in `docs/architecture.md` and
`docs/payment-safety.md`.
