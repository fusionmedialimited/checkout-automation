# InvestingPro Checkout Automation

Java + Playwright + Cucumber automation framework for the InvestingPro checkout journey
(master QA: `https://master--www.ams-qa.finboxgcp.investing.com/pro/`).

This is a **scaffold**: it establishes structure, configuration, safety gates, browser
lifecycle, and one smoke scenario. It does not yet implement checkout, user provisioning, or
payment flows — see `docs/checkout-testing.md` for exactly what's built vs. still required.

## Requirements

- JDK 21 (any distribution). The Maven wrapper pins Maven 3.9.9 for you.
- No other local install is required to run offline tests. Running the QA smoke suite also
  needs Chromium, installed via Playwright's own installer (see below) — Playwright will tell
  you if it's missing rather than failing silently.

## Commands

```bash
# Offline: config/safety-gate unit tests only. No browser, no network target.
./mvnw test

# One-time (or after upgrading the Playwright dependency): install the Chromium build
# Playwright's Java bindings expect.
./mvnw -q compile
./mvnw -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=com.microsoft.playwright.CLI \
  -Dexec.classpathScope=test \
  -Dexec.args="install --with-deps chromium"

# QA smoke: loads the master QA landing page only. No user creation, no payment.
./mvnw test -Dtest=SmokeTestRunner
```

There is currently no command for QA sandbox checkout or authorized real-payment runs — those
runner classes don't exist yet (see `docs/checkout-testing.md`).

## Project layout

```
.
├── pom.xml, mvnw, mvnw.cmd, .mvn/wrapper/
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
