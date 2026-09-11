package com.investing.pro.safety;

import com.investing.pro.config.Config;
import com.investing.pro.config.ConfigValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("PaymentSafetyGate: user-creation, sandbox-purchase, and real-card-purchase authorization")
class PaymentSafetyGateTest {

    @BeforeEach
    void isolateFromRealEnvironment() {
        // PaymentSafetyGate resolves qa.allow*/qa.paymentMode/qa.realCardAuthorizationRef
        // through Config, which falls back to real environment variables (QA_ALLOW*,
        // QA_PAYMENTMODE, ...) when no system property is set. Without this, an actually-exported
        // QA_* variable would make the default-disabled and missing-mode tests below pass or fail
        // for the wrong reason.
        Config.useEnvironmentForTesting(key -> null);
    }

    @AfterEach
    void clearAuthorizationFlags() {
        System.clearProperty("qa.allowUserCreation");
        System.clearProperty("qa.allowSandboxPurchase");
        System.clearProperty("qa.allowRealCardPurchase");
        System.clearProperty("qa.paymentMode");
        System.clearProperty("qa.realCardAuthorizationRef");
        Config.resetEnvironmentForTesting();
    }

    @Test
    @DisplayName("requireUserCreationAuthorized throws when qa.allowUserCreation is unset (default false)")
    void requireUserCreationAuthorized_throwsByDefault() {
        assertThrows(SafetyViolationException.class, PaymentSafetyGate::requireUserCreationAuthorized);
    }

    @Test
    @DisplayName("requireUserCreationAuthorized passes once qa.allowUserCreation=true is set explicitly")
    void requireUserCreationAuthorized_passesWhenExplicitlyEnabled() {
        System.setProperty("qa.allowUserCreation", "true");
        assertDoesNotThrow(PaymentSafetyGate::requireUserCreationAuthorized);
    }

    @Test
    @DisplayName("requireSandboxPurchaseAuthorized throws when qa.allowSandboxPurchase is unset (default false)")
    void requireSandboxPurchaseAuthorized_throwsWhenFlagDisabled() {
        assertThrows(SafetyViolationException.class, PaymentSafetyGate::requireSandboxPurchaseAuthorized);
    }

    @Test
    @DisplayName("requireSandboxPurchaseAuthorized throws when the flag is set but qa.paymentMode is real, not sandbox")
    void requireSandboxPurchaseAuthorized_throwsWhenFlagEnabledButPaymentModeIsNotSandbox() {
        System.setProperty("qa.allowSandboxPurchase", "true");
        System.setProperty("qa.paymentMode", "real");

        assertThrows(SafetyViolationException.class, PaymentSafetyGate::requireSandboxPurchaseAuthorized);
    }

    @Test
    @DisplayName("requireSandboxPurchaseAuthorized fails closed with ConfigValidationException when qa.paymentMode is never set")
    void requireSandboxPurchaseAuthorized_throwsWhenPaymentModeUnset() {
        System.setProperty("qa.allowSandboxPurchase", "true");

        assertThrows(ConfigValidationException.class, PaymentSafetyGate::requireSandboxPurchaseAuthorized);
    }

    @Test
    @DisplayName("requireSandboxPurchaseAuthorized passes when both the flag is true and qa.paymentMode=sandbox")
    void requireSandboxPurchaseAuthorized_passesWhenFlagEnabledAndPaymentModeIsSandbox() {
        System.setProperty("qa.allowSandboxPurchase", "true");
        System.setProperty("qa.paymentMode", "sandbox");

        assertDoesNotThrow(PaymentSafetyGate::requireSandboxPurchaseAuthorized);
    }

    @Test
    @DisplayName("requireRealCardPurchaseAuthorized throws when qa.allowRealCardPurchase is unset (default false)")
    void requireRealCardPurchaseAuthorized_throwsWhenFlagDisabled() {
        assertThrows(SafetyViolationException.class, PaymentSafetyGate::requireRealCardPurchaseAuthorized);
    }

    @Test
    @DisplayName("requireRealCardPurchaseAuthorized throws when the flag and mode are set but qa.realCardAuthorizationRef is missing")
    void requireRealCardPurchaseAuthorized_throwsWhenAuthorizationRefIsMissing() {
        System.setProperty("qa.allowRealCardPurchase", "true");
        System.setProperty("qa.paymentMode", "real");

        assertThrows(SafetyViolationException.class, PaymentSafetyGate::requireRealCardPurchaseAuthorized);
    }

    @Test
    @DisplayName("requireRealCardPurchaseAuthorized passes only when the flag, real payment mode, and authorization ref are all present")
    void requireRealCardPurchaseAuthorized_passesOnlyWhenFlagModeAndReferenceAllPresent() {
        System.setProperty("qa.allowRealCardPurchase", "true");
        System.setProperty("qa.paymentMode", "real");
        System.setProperty("qa.realCardAuthorizationRef", "TICKET-1234");

        assertDoesNotThrow(PaymentSafetyGate::requireRealCardPurchaseAuthorized);
    }
}
