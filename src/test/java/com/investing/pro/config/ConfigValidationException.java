package com.investing.pro.config;

/** Thrown when configuration is missing, ambiguous, or invalid. Fails closed by design. */
public class ConfigValidationException extends RuntimeException {

    public ConfigValidationException(String message) {
        super(message);
    }

    public ConfigValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
