# CLAUDE.md

Project instructions for Claude Code working in this repository. Read `docs/architecture.md`,
`docs/checkout-testing.md`, and `docs/payment-safety.md` before making non-trivial changes —
this file summarizes and points into them, it doesn't replace them.

## Build and test commands

```bash
./mvnw test                                # offline: config/safety-gate tests only, no browser
./mvnw test -Dtest=SmokeTestRunner          # QA smoke: loads master QA landing page only
./mvnw test-compile                         # compile without running anything
```

There is no command for QA sandbox checkout or real-payment runs yet — no runner class exists
for either (see `docs/checkout-testing.md`). Do not invent one to "complete" a task; document
the gap instead.

## Architecture and coding conventions

- Everything lives under `src/test` (test-only framework, no `src/main`).
- Locators and raw Playwright calls: `pages/` only. Business-readable steps: `steps/` only,
  delegating to `pages/`, future `services/`, and `safety/`. Per-scenario state: `context/`,
  injected by PicoContainer (`cucumber-picocontainer`) — never a static field.
- `mvn test` (no args) must never touch a browser or network target. This works because Cucumber
  suite runners under `runners/` are named so they don't match surefire's default `*Test.java`
  pattern; keep that naming convention for any new runner.
- Full detail: `docs/architecture.md`.

## Checkout scope

In scope (see `docs/checkout-testing.md` for what's implemented vs. not): user creation/sign-in,
plan selection, coupon application, discount/currency/billing-period/amount-due verification,
sandbox and authorized real-card purchase, purchase confirmation, subscription-state validation,
cleanup. Only the smoke scenario (landing page loads, no user/payment) is implemented today.
Don't fabricate selectors, coupon rules, plan IDs, or a provisioning API to fill gaps — extend
the "Explicitly not implemented" section of `docs/checkout-testing.md` instead.

## User provisioning

No approved provisioning API or internal tool exists yet. Prefer one over UI registration once
it does; use UI registration only when registration itself is under test. Never invent a
registration endpoint or an email-verification bypass. See `docs/checkout-testing.md` →
"User provisioning" for the intended contract shape.

## Environment vs. payment mode

`qa.baseUrl` (application environment) and `qa.paymentMode` (sandbox/real) are separate
settings — a QA URL does not prove the payment processor is sandboxed. `qa.paymentMode` has no
checked-in default and throws until set explicitly. Full precedence rules: `docs/architecture.md`.

## Payment execution gates

`PaymentSafetyGate` is the only place allowed to authorize user creation or a purchase; every
such step definition must call it first. All three flags (`qa.allowUserCreation`,
`qa.allowSandboxPurchase`, `qa.allowRealCardPurchase`) default to `false`. A Cucumber tag never
substitutes for calling the gate. Real-card purchases need a fourth thing —
`qa.realCardAuthorizationRef` — pointing at a human-approved authorization record; see
`docs/payment-safety.md` for what that record must contain before it's issued. Never bypass
CAPTCHA, 3-D Secure, or fraud controls.

## Sensitive artifact handling

No raw card data in prompts, feature files, source, logs, or reports — ever, sandbox or real.
Screenshots/traces are not automatically redacted; a future card-entry step must suppress
capture around itself rather than relying on the scenario-level `qa.artifactPolicy`. See
`docs/payment-safety.md`.

## Failure investigation and ambiguous payments

Read the actual failure (surefire report, Cucumber JSON/HTML under `target/`, screenshot/trace
under `target/artifacts`) before proposing a fix — don't guess from the scenario name. Never
add an automatic retry around a payment submission: an ambiguous outcome (timeout, unclear
response) must be reconciled against the payment provider and/or subscription state before any
second attempt, using the provider's idempotency mechanism once purchase submission exists.

## Definition of done

A change is done when: it compiles (`./mvnw test-compile`), offline tests pass (`./mvnw test`),
any new chargeable action is gated through `PaymentSafetyGate`, any new selector was verified
against a real page (or explicitly labeled unvalidated if it couldn't be), no secret or raw card
data was added anywhere, and the relevant doc under `docs/` was updated in the same change — not
left to drift.

## MCP (development-time only, not committed)

MCP tools assist development and investigation; the Java suite must run without Claude or MCP.
Do not add a runtime dependency on any MCP tool inside `src/test`. See `docs/mcp-setup.md` for
what was available in this environment (browser automation for read-only UI discovery, a
read-only Postgres query skill, Stripe/Recurly MCP tools of unconfirmed relevance) and what
wasn't (GitHub access, an internal provisioning tool). If you set up a new MCP server locally,
configure it in your own Claude Code settings — never commit credentials, and never make
`src/test` depend on one being present.
