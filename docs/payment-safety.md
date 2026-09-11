# Payment safety

## Environment and payment mode are separate settings

`qa.baseUrl` says which application build the browser talks to. `qa.paymentMode` says whether
the payment processor behind checkout is expected to be in `sandbox` or `real` mode. **A QA URL
proves nothing about the second in general.** `qa.paymentMode` has no checked-in default; any
code path that needs it (via `Config.paymentMode()`) throws `ConfigValidationException` until it
is set explicitly. Setting `qa.paymentMode=sandbox` should only happen once the integration has
actually been verified as sandboxed — not assumed from the environment name.

For InvestingPro specifically, the project owner confirmed (2026-09-11) that every QA payment
processor is always sandboxed. This satisfies that verification bar for any workflow that only
ever targets a QA environment: such a workflow may declare `qa.paymentMode=sandbox` explicitly
(e.g. as a CI-level environment variable, not a code change), rather than leaving it unset. The
checked-in default in `default.properties` must still not be `sandbox` — that default protects
any run that isn't explicitly QA-scoped (local runs against an unknown target, or a future
production/real-card workflow) from silently assuming sandbox just because no value was given.

## The gates

All enforcement lives in `PaymentSafetyGate` (`src/test/java/.../safety/PaymentSafetyGate.java`).
Every future step definition that creates a user or submits a payment must call the matching
method *before* performing the action:

| Action | Gate method | Requires |
|---|---|---|
| Create a test user | `requireUserCreationAuthorized()` | `qa.allowUserCreation=true` |
| Submit a sandbox purchase | `requireSandboxPurchaseAuthorized()` | `qa.allowSandboxPurchase=true` **and** `qa.paymentMode=sandbox` |
| Submit a real-card purchase | `requireRealCardPurchaseAuthorized()` | `qa.allowRealCardPurchase=true` **and** `qa.paymentMode=real` **and** a non-blank `qa.realCardAuthorizationRef` |

All three flags default to `false`. None of them can be satisfied by a Cucumber tag alone —
tags select *which* scenarios run, they do not authorize what those scenarios are allowed to do.
This is intentional: a mistagged or copy-pasted feature file must not be able to submit a real
charge.

The real-card gate is deliberately the strongest of the three. `qa.realCardAuthorizationRef` is
just a reference string (e.g. a ticket id) the gate requires to be present — it is not validated
against anything yet, because no authorization-record system exists in this phase. Before any
real-card run is actually executed, a human must have separately produced and approved a record
covering:

- Target environment
- Approved payment-method secret reference (never the raw card data itself)
- Plan and expected currency
- Maximum charge and transaction count
- Coupon, if applicable
- Execution window
- Cancellation/refund expectations and the responsible owner

None of that record-keeping is implemented yet; `qa.realCardAuthorizationRef` is the hook future
work should attach it to.

## Secrets and sensitive data

- No raw card data anywhere: not in prompts, feature files, Java source, logs, or reports. Use
  provider-supported test card numbers/tokens for sandbox; use an approved secret store
  reference (never the secret itself in config) for any authorized real-payment credential.
- `logback-test.xml` has no logger configured for card or payment-provider request/response
  bodies. Do not add one without a reviewed redaction policy.
- Screenshots, traces, and video are not automatically redacted by Playwright. Until an approved
  capture/redaction policy exists, any future card-entry step must call
  `BrowserResources.finishTracing(false, ...)` / `finishVideo(false, ...)`-equivalent handling
  (or otherwise suppress capture) around that step rather than relying on the scenario-level
  artifact policy.
- CAPTCHA, payment authentication (3-D Secure), and fraud controls must never be bypassed by
  automation. Document the approved test mechanism (e.g. a provider-supplied bypass card) or
  treat the flow as a manual-only boundary.

## Ambiguous outcomes and retries

Do not automatically retry a payment submission after a timeout or unclear response — a
duplicate charge is worse than a failed test run. An ambiguous outcome must be reconciled
(checked against the payment provider and/or subscription state) before attempting again. Use
the payment provider's idempotency-key mechanism wherever it is supported once purchase
submission is implemented.

`qa-smoke.yml` now supports opt-in retries (a `workflow_dispatch` choice input, default `0`) for
`SmokeTestRunner`, reusing Cucumber's `rerun:` plugin to retry only the scenarios that actually
failed — never a blanket rerun of the whole suite. This is safe *only* because that suite has no
scenario that creates a user or submits a payment. Before pointing this same mechanism at any
future runner that does: a payment-submitting scenario must never be included in a
retry-enabled suite/tag unless its outcome has first been reconciled against the payment
provider and/or subscription state as described above — rerunning it blindly is exactly the
duplicate-charge risk this section exists to prevent.

## Cleanup is not one operation

Account deletion, subscription cancellation, and refund are three different operations against
three different systems. Nothing in this framework should ever claim that performing one
implies another happened — see `CleanupStatus` in `model/`, which enumerates them separately
rather than exposing a single boolean "cleaned up" flag. Cleanup must never suppress or hide the
original scenario failure it ran after.
