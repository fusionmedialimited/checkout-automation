# MCP / agent tooling (development-time only)

**Nothing in this document affects the Java test suite at runtime.** `./mvnw test` and
`-Dtest=SmokeTestRunner` run as plain JVM processes with no dependency on Claude, MCP, or any
of the tools below. This file exists to record what was available to Claude Code *while
building and will be available while extending* this framework, so future contributors don't
have to rediscover it.

## What was discovered in this environment

- **GitHub**: no GitHub MCP tool and no authenticated `gh` CLI were available. The reference
  repository could not be inspected (see `docs/architecture.md`). If a GitHub MCP server or an
  authenticated `gh` is set up later, re-run that discovery.
- **Browser (`claude-in-chrome`)**: available, and used during this setup phase to confirm the
  master QA landing page actually renders (it's a client-side SPA — a plain HTTP fetch returns
  no usable body) and to record the one locator used by the smoke test. Continue using it for
  read-only UI discovery (identifying selectors, confirming a flow's shape) — never for creating
  accounts or submitting payments; those require the explicit authorization described in
  `docs/payment-safety.md` regardless of what the browser tool could technically do.
- **`query-postgres` skill**: runs read-only SQL against the Investing.com Postgres instance
  (subscriptions, auth, id_mapping, configdb). Useful for a human/Claude to verify a test user's
  actual subscription state while debugging a failing scenario — not usable by the Java suite
  itself. See `docs/checkout-testing.md`.
- **Stripe MCP and Recurly MCP**: both tools were available in this environment. Their presence
  suggests one or both may be relevant to InvestingPro's payment stack, but this was not
  confirmed against the real checkout integration during this setup phase — treat it as a lead
  to verify, not a fact to build on.
- **No dedicated internal user-provisioning MCP tool** was found.

## Setting up MCP servers

This project does not commit any MCP server configuration or credentials, and none were
installed as part of this setup. If you add one (e.g. a GitHub MCP server for reference-repo
access, or a provisioning tool once one is approved), configure it in your own Claude Code
settings — do not commit tokens, and do not add a dependency on it to any file under
`src/test` (the Java suite must keep working with MCP absent).
