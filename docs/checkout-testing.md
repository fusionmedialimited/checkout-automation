# Checkout testing scope

## In scope for the framework (not all implemented yet)

- Create new test users
- Sign in existing test users
- Select subscription plans
- Apply coupon codes
- Verify discount, currency, billing period, and amount due
- Complete purchase with supported test cards (sandbox)
- Support explicitly authorized real-card purchase tests
- Validate purchase confirmation and subscription state
- Clean up test accounts / subscription data where supported

## Implemented in this phase

- Project scaffold, dependency versions, Gradle build, wrapper
- Configuration precedence and validation (`Config`)
- Payment/user-creation safety gates (`PaymentSafetyGate`) — enforcement only; nothing calls
  them yet because nothing creates users or purchases yet
- Scenario-scoped browser lifecycle and artifact capture — tracing, screenshots, and video —
  (`BrowserResources`, `Hooks`), all governed by the same `qa.artifactPolicy`
- One smoke scenario: `features/smoke/landing_page.feature`, tagged `@smoke @qa`, verified
  against a live browser session against master QA on 2026-09-09 (see `LandingPage` Javadoc).
  It only asserts the page loads — no user creation, no purchase.
- Offline tests for configuration precedence and safety-gate behavior
- Allure reporting for `SmokeTestRunner` (raw results, uploaded as a CI artifact only — not
  pushed to any shared portal yet), replacing Cucumber's own HTML/JSON report; unit tests keep
  plain JUnit XML reports; see `docs/architecture.md` "Reporting"
- Opt-in, scenario-scoped retry for `SmokeTestRunner` only (`qa-smoke.yml`'s `retry` input); see
  `docs/payment-safety.md` "Ambiguous outcomes and retries" for why this must not be reused for a
  suite that submits payments without re-reading that section first
- A Slack-on-failure extension point in `qa-smoke.yml`, currently a no-op because no webhook
  secret has been configured for this project yet

## Explicitly not implemented — required contracts

### User provisioning

No provisioning API or internal tool is confirmed approved for this project's use, so this is
left unimplemented rather than invented, per the task's own instructions. That said, a
**candidate** is no longer purely hypothetical: `fusionmedialimited/Cucumber-Playwright-POC`'s
`UserApiHandler` calls internal, undocumented-to-us endpoints — `/members-admin/auth/signUpByEmail`,
`/members-admin/auth/signInByEmail`, a mobile-API temp-user endpoint
(`login_api.php?data={"action":"register_anon"}...`), `/members-admin/service/verifyEmailCode`,
and `/dev-tools/removeUserAutomation.php` / `/dev-tools/removeUser.php` for cleanup — which
proves *some* internal mechanism exists in the org, not that it's approved for this project.
Before adopting any of it: confirm with whoever owns those endpoints that this project may call
them, confirm they behave the same against master QA as they do wherever that reference project
targets them, and confirm they're not scoped/rate-limited in a way that assumes a single
sibling project's usage pattern. Until then, the future `provisioning` package should expose
something like:

```java
public interface UserProvisioningService {
    TestUserRef createUser(TestUserRequest request);   // via an approved API, not raw HTTP guesswork
    void deleteUser(String userId);                     // only if the provisioning system supports it
}
```

backed by whatever approved API or internal tool is confirmed to exist, with UI-based
registration only used when registration itself is the thing under test.

### Test email / verification

No approved test email domain or inbox service (e.g. a disposable-inbox API) was identified. The
same reference project's `getEmailVerificationCode`/`requestVerificationCodeWithRetries` (POSTing
to `/dev-tools/getVerificationCode.php`, retrying since the code can arrive with a delay) is the
same kind of unconfirmed-but-real candidate as the provisioning endpoints above — same caveat
applies before this project relies on it.

### Plan and coupon fixtures

No approved list of real plan IDs, prices, currencies, or coupon codes was supplied or
discovered. `qa.approvedPlansFile` / `qa.approvedCouponsFile` are anticipated config keys (not
yet in `default.properties`) for pointing at such fixtures once they exist. Do not brute-force
or guess coupon codes, and do not create/modify coupons without explicit permission.

### Subscription-state lookup

`query-postgres` (a Claude Code skill available in this environment) can run read-only SQL
against the Investing.com Postgres instance, including subscription and auth tables — this is a
credible candidate for verifying subscription state *during test development/debugging*, but it
is a Claude-side tool, not something the Java suite can call at runtime (the suite must run
without Claude or MCP — see `docs/mcp-setup.md`). A runtime subscription-state check needs an
approved, directly-callable API or DB credential the Java suite can use on its own; that does
not exist yet.

### Payment provider / sandbox verification

This environment has Stripe and Recurly MCP tools available to Claude, which suggests one or
both may be InvestingPro's payment processor(s) — but this was not confirmed against the actual
checkout integration, and MCP access does not carry over to the Java test suite regardless (see
`docs/mcp-setup.md`). Before implementing sandbox checkout tests: confirm which processor is
live behind master QA, confirm it is actually configured in sandbox/test mode there, and obtain
provider-supported test card numbers or tokens.

## Coverage matrix (future — not implemented)

| Area | Scenarios |
|---|---|
| Successful checkout | New user, existing user, each supported plan/billing period |
| Coupon eligibility | Valid coupon on eligible plan, coupon on ineligible plan, expired coupon, invalid code, stacking rules (if any) |
| Declined payments | Provider-supported decline test card, insufficient funds, 3-D Secure challenge (manual/documented boundary) |
| Existing subscribers | Upgrade, downgrade, renewal, duplicate-purchase prevention |
| Authentication | Sign-in during checkout, session already authenticated, sign-up during checkout |

Do not implement this matrix now; it's recorded so future work has an agreed shape rather than
being invented scenario-by-scenario.

## Execution paths

| Path | Command | Touches network/browser? | Can create users / pay? |
|---|---|---|---|
| Offline validation | `./gradlew test` | No | No |
| QA smoke | `./gradlew qaSmokeTest` | Yes (master QA only) | No |
| QA sandbox checkout | *(future — no runner or Gradle task exists yet)* | Yes | Only with `qa.allowUserCreation`/`qa.allowSandboxPurchase` set |
| Authorized real-payment | *(future — no runner or Gradle task exists yet)* | Yes | Only with the full real-card gate satisfied, see `docs/payment-safety.md` |

An unsupported path (sandbox checkout, real-payment) has no runner class or Gradle task at all
right now, so there is nothing to invoke for it — a future runner needs both a new `runners`
class and its own dedicated task in `build.gradle` (following `qaSmokeTest`'s pattern), not a
`--tests` filter added to an existing task.

None of InvestingPro's QA environments are publicly reachable, so any path that touches one in
CI needs a runner with internal network access — see `docs/architecture.md` → "CI runners" for
which runner each workflow uses and why. A future sandbox-checkout or real-payment runner will
need the same kind of runner-access review before its workflow is created, independent of
whether it reuses the same runner pool.

## Definition of done (this phase)

- [x] Project compiles: `./gradlew testClasses`
- [x] Offline tests pass: `./gradlew test`
- [ ] QA smoke run executed and confirmed green against `./gradlew qaSmokeTest`: previously
      verified green (both headless and headed, locally) under the Maven-era
      `./mvnw test -Dtest=SmokeTestRunner`, on 2026-09-09 — not yet re-verified under Gradle
      against the real target; see `docs/architecture.md` "CI runners" for what qa-smoke.yml now
      uses. Leave this unchecked until an actual `qaSmokeTest` run against master QA is confirmed.
- [x] CI runs compilation + offline tests on PR/push, and QA smoke only as a manual, artifact
      uploading workflow
- [x] No fabricated APIs, selectors (beyond what was live-verified), coupon rules, or
      provisioning mechanisms
