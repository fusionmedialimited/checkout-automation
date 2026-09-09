package com.investing.pro.safety;

import com.investing.pro.config.ConfigValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentSafetyGateTest {

    @AfterEach
    void clearAuthorizationFlags() {
        System.clearProperty("qa.allowUserCreation");
        System.clearProperty("qa.allowSandboxPurchase");
        System.clearProperty("qa.allowRealCardPurchase");
        System.clearProperty("qa.paymentMode");
        System.clearProperty("qa.realCardAuthorizationRef");
    }

    @Test
    void requireUserCreationAuthorized_throwsByDefault() {
        assertThrows(SafetyViolationException.class, PaymentSafetyGate::requireUserCreationAuthorized);
    }

    @Test
    void requireUserCreationAuthorized_passesWhenExplicitlyEnabled() {
        System.setProperty("qa.allowUserCreation", "true");
        assertDoesNotThrow(PaymentSafetyGate::requireUserCreationAuthorized);
    }

    @Test
    void requireSandboxPurchaseAuthorized_throwsWhenFlagDisabled() {
        assertThrows(SafetyViolationException.class, PaymentSafetyGate::requireSandboxPurchaseAuthorized);
    }

    @Test
    void requireSandboxPurchaseAuthorized_throwsWhenFlagEnabledButPaymentModeIsNotSandbox() {
        System.setProperty("qa.allowSandboxPurchase", "true");
        System.setProperty("qa.paymentMode", "real");

        assertThrows(SafetyViolationException.class, PaymentSafetyGate::requireSandboxPurchaseAuthorized);
    }

    @Test
    void requireSandboxPurchaseAuthorized_throwsWhenPaymentModeUnset() {
        System.setProperty("qa.allowSandboxPurchase", "true");

        assertThrows(ConfigValidationException.class, PaymentSafetyGate::requireSandboxPurchaseAuthorized);
    }

    @Test
    void requireSandboxPurchaseAuthorized_passesWhenFlagEnabledAndPaymentModeIsSandbox() {
        System.setProperty("qa.allowSandboxPurchase", "true");
        System.setProperty("qa.paymentMode", "sandbox");

        assertDoesNotThrow(PaymentSafetyGate::requireSandboxPurchaseAuthorized);
    }

    @Test
    void requireRealCardPurchaseAuthorized_throwsWhenFlagDisabled() {
        assertThrows(SafetyViolationException.class, PaymentSafetyGate::requireRealCardPurchaseAuthorized);
    }

    @Test
    void requireRealCardPurchaseAuthorized_throwsWhenAuthorizationRefIsMissing() {
        System.setProperty("qa.allowRealCardPurchase", "true");
        System.setProperty("qa.paymentMode", "real");

        assertThrows(SafetyViolationException.class, PaymentSafetyGate::requireRealCardPurchaseAuthorized);
    }

    @Test
    void requireRealCardPurchaseAuthorized_passesOnlyWhenFlagModeAndReferenceAllPresent() {
        System.setProperty("qa.allowRealCardPurchase", "true");
        System.setProperty("qa.paymentMode", "real");
        System.setProperty("qa.realCardAuthorizationRef", "TICKET-1234");

        assertDoesNotThrow(PaymentSafetyGate::requireRealCardPurchaseAuthorized);
    }
}
