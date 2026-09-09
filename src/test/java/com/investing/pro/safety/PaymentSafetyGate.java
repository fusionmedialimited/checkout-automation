package com.investing.pro.safety;

import com.investing.pro.config.Config;
import com.investing.pro.config.PaymentMode;

/**
 * Single point of enforcement for every action this framework treats as potentially chargeable
 * or account-mutating. Step definitions must call the relevant {@code requireXxxAuthorized()}
 * method before creating a user or submitting a payment; nothing here infers authorization from
 * a Cucumber tag or the mere presence of an environment variable, per the project's payment
 * safety rules (see docs/payment-safety.md).
 */
public final class PaymentSafetyGate {

    private PaymentSafetyGate() {
    }

    public static void requireUserCreationAuthorized() {
        if (!Config.allowUserCreation()) {
            throw new SafetyViolationException(
                    "Test-user creation is disabled by default. Set qa.allowUserCreation=true "
                            + "(JVM property) or " + Config.envKey("qa.allowUserCreation")
                            + "=true (environment variable) for an explicitly authorized run.");
        }
    }

    public static void requireSandboxPurchaseAuthorized() {
        if (!Config.allowSandboxPurchase()) {
            throw new SafetyViolationException(
                    "Sandbox purchase submission is disabled by default. Set "
                            + "qa.allowSandboxPurchase=true (JVM property) or "
                            + Config.envKey("qa.allowSandboxPurchase")
                            + "=true (environment variable) for an explicitly authorized run.");
        }
        PaymentMode mode = Config.paymentMode();
        if (mode != PaymentMode.SANDBOX) {
            throw new SafetyViolationException(
                    "Sandbox purchase requested but qa.paymentMode=" + mode
                            + ". A QA application URL does not prove the payment processor is in "
                            + "sandbox mode; set qa.paymentMode=sandbox only once that has been "
                            + "verified.");
        }
    }

    /**
     * Real-card purchases require a stronger gate than sandbox: the boolean flag alone is not
     * sufficient, an explicit authorization reference must also be present. This method does not
     * itself validate the referenced authorization record (target environment, plan, currency,
     * maximum charge, execution window, refund owner) — that review happens before the reference
     * is issued; see docs/payment-safety.md.
     */
    public static void requireRealCardPurchaseAuthorized() {
        if (!Config.allowRealCardPurchase()) {
            throw new SafetyViolationException(
                    "Real-card purchase submission is disabled by default. Set "
                            + "qa.allowRealCardPurchase=true (JVM property) or "
                            + Config.envKey("qa.allowRealCardPurchase")
                            + "=true (environment variable) for an explicitly authorized run.");
        }
        PaymentMode mode = Config.paymentMode();
        if (mode != PaymentMode.REAL) {
            throw new SafetyViolationException(
                    "Real-card purchase requested but qa.paymentMode=" + mode
                            + "; expected 'real'.");
        }
        String authorizationRef = Config.realCardAuthorizationRef();
        if (authorizationRef.isBlank()) {
            throw new SafetyViolationException(
                    "Real-card purchase requires qa.realCardAuthorizationRef pointing at an "
                            + "approved authorization record (target environment, payment-method "
                            + "secret reference, plan/currency, maximum charge and transaction "
                            + "count, coupon if any, execution window, and refund owner). A tag or "
                            + "environment variable alone is not proof of authorization.");
        }
    }
}
