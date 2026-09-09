package com.investing.pro.config;

/**
 * The payment processor's expected mode, configured independently of the application
 * environment: a QA application URL does not by itself prove the payment processor behind it
 * is in sandbox mode, so this must be set explicitly wherever payment execution is authorized.
 */
public enum PaymentMode {
    SANDBOX,
    REAL;

    static PaymentMode fromConfigValue(String value) {
        try {
            return PaymentMode.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ConfigValidationException(
                    "Invalid qa.paymentMode '" + value + "'; expected 'sandbox' or 'real'.");
        }
    }
}
