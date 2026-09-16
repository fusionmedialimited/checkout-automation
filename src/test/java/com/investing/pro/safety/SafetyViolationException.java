package com.investing.pro.safety;

/** Thrown when a scenario attempts an action it is not explicitly authorized to perform. */
public class SafetyViolationException extends RuntimeException {

    public SafetyViolationException(String message) {
        super(message);
    }
}
