package com.investing.pro.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Config: system-property > environment-variable > checked-in-default precedence, with fail-closed lookups")
class ConfigPrecedenceTest {

    private static final String KEY = "qa.someSetting";

    @BeforeEach
    void isolateFromRealEnvironment() {
        // Tests below that call the real Config.baseUrl()/allowUserCreation()/etc. (not the
        // resolve()-with-injected-functions ones) go through the real environment-variable
        // lookup too. Without this, an actually-exported QA_* variable (e.g. from sourcing
        // .env.example locally) would leak in and make default/missing-value assertions pass or
        // fail for the wrong reason.
        Config.useEnvironmentForTesting(key -> null);
    }

    @AfterEach
    void clearRealSystemProperties() {
        System.clearProperty(KEY);
        System.clearProperty("qa.baseUrl");
        System.clearProperty("qa.allowUserCreation");
        System.clearProperty("qa.paymentMode");
        System.clearProperty("qa.timeoutMs");
        Config.resetEnvironmentForTesting();
    }

    @Test
    @DisplayName("resolve() prefers a system property over an environment variable or default")
    void resolve_prefersSystemPropertyOverEnvAndDefault() {
        Properties defaults = defaultsWith(KEY, "from-default");
        String result = Config.resolve(KEY,
                key -> "from-property",
                key -> "from-env",
                defaults);

        assertEquals("from-property", result);
    }

    @Test
    @DisplayName("resolve() falls back to the environment variable when no system property is set")
    void resolve_prefersEnvironmentOverDefault_whenNoSystemProperty() {
        Properties defaults = defaultsWith(KEY, "from-default");
        String result = Config.resolve(KEY,
                key -> null,
                key -> "from-env",
                defaults);

        assertEquals("from-env", result);
    }

    @Test
    @DisplayName("resolve() falls back to the checked-in default when neither a system property nor an environment variable is set")
    void resolve_fallsBackToDefault_whenNothingElseSet() {
        Properties defaults = defaultsWith(KEY, "from-default");
        String result = Config.resolve(KEY, key -> null, key -> null, defaults);

        assertEquals("from-default", result);
    }

    @Test
    @DisplayName("resolve() treats blank system-property and environment values as absent and falls through to the default")
    void resolve_ignoresBlankSystemPropertyAndEnvironmentValues() {
        Properties defaults = defaultsWith(KEY, "from-default");
        String result = Config.resolve(KEY, key -> "  ", key -> "", defaults);

        assertEquals("from-default", result);
    }

    @Test
    @DisplayName("resolve() returns null when no layer (property, env, default) provides a value")
    void resolve_returnsNull_whenNoSourceHasAValue() {
        String result = Config.resolve(KEY, key -> null, key -> null, new Properties());
        assertNull(result);
    }

    @Test
    @DisplayName("resolve() trims surrounding whitespace from whichever layer's value is chosen")
    void resolve_trimsSurroundingWhitespaceFromTheResolvedValue() {
        Properties defaults = defaultsWith(KEY, "from-default");
        assertEquals("from-property", Config.resolve(KEY, key -> " from-property ", key -> null, defaults));
        assertEquals("from-env", Config.resolve(KEY, key -> null, key -> " from-env ", defaults));
        assertEquals("from-default", Config.resolve(KEY, key -> null, key -> null, defaultsWith(KEY, " from-default ")));
    }

    @Test
    @DisplayName("envKey() converts a dotted property key into its upper-cased, underscore-separated environment-variable name")
    void envKey_upperCasesAndReplacesDotsWithUnderscores() {
        assertEquals("QA_ALLOW_USER_CREATION", Config.envKey("qa.allow.user.creation"));
    }

    @Test
    @DisplayName("get() throws ConfigValidationException, naming the missing key, when it is absent from every layer")
    void get_throwsConfigValidationException_whenKeyIsMissingEverywhere() {
        ConfigValidationException exception = assertThrows(ConfigValidationException.class,
                () -> Config.get("qa.thisKeyDoesNotExistAnywhere"));

        assertTrue(exception.getMessage().contains("qa.thisKeyDoesNotExistAnywhere"));
    }

    @Test
    @DisplayName("baseUrl() returns the checked-in default QA URL when not overridden")
    void baseUrl_returnsCheckedInDefault_whenNotOverridden() {
        assertEquals("https://master--www.ams-qa.finboxgcp.investing.com/pro/", Config.baseUrl());
    }

    @Test
    @DisplayName("allowUserCreation() defaults to false when unset")
    void allowUserCreation_defaultsToFalse() {
        assertEquals(false, Config.allowUserCreation());
    }

    @Test
    @DisplayName("allowUserCreation() can be overridden to true via a system property")
    void allowUserCreation_canBeOverriddenBySystemProperty() {
        System.setProperty("qa.allowUserCreation", "true");
        assertTrue(Config.allowUserCreation());
    }

    @Test
    @DisplayName("Boolean-valued config tolerates surrounding whitespace around true/false")
    void getBoolean_toleratesSurroundingWhitespace() {
        System.setProperty("qa.allowUserCreation", " true ");
        assertTrue(Config.allowUserCreation());
    }

    @Test
    @DisplayName("Boolean-valued config rejects any value that isn't exactly \"true\" or \"false\"")
    void getBoolean_rejectsValuesThatAreNotExactlyTrueOrFalse() {
        System.setProperty("qa.allowUserCreation", "treu");
        assertThrows(ConfigValidationException.class, Config::allowUserCreation);
    }

    @Test
    @DisplayName("timeoutMs() returns the checked-in default timeout when not overridden")
    void timeoutMs_returnsCheckedInDefault_whenNotOverridden() {
        assertEquals(30000, Config.timeoutMs());
    }

    @Test
    @DisplayName("timeoutMs() rejects a non-integer value with ConfigValidationException rather than a raw NumberFormatException")
    void timeoutMs_rejectsNonIntegerValue_withConfigValidationExceptionNotNumberFormatException() {
        System.setProperty("qa.timeoutMs", "not-a-number");
        assertThrows(ConfigValidationException.class, Config::timeoutMs);
    }

    @Test
    @DisplayName("timeoutMs() rejects zero, since Playwright treats a zero timeout as disabled rather than immediate")
    void timeoutMs_rejectsZero() {
        System.setProperty("qa.timeoutMs", "0");
        assertThrows(ConfigValidationException.class, Config::timeoutMs);
    }

    @Test
    @DisplayName("timeoutMs() rejects a negative value")
    void timeoutMs_rejectsNegativeValue() {
        System.setProperty("qa.timeoutMs", "-1");
        assertThrows(ConfigValidationException.class, Config::timeoutMs);
    }

    @Test
    @DisplayName("paymentMode() has no checked-in default and fails closed (throws) when qa.paymentMode is unset")
    void paymentMode_hasNoDefault_andFailsClosed() {
        assertThrows(ConfigValidationException.class, Config::paymentMode);
    }

    @Test
    @DisplayName("paymentMode() parses \"sandbox\" and \"REAL\" (case-insensitive) into the matching PaymentMode")
    void paymentMode_parsesConfiguredSandboxAndReal() {
        System.setProperty("qa.paymentMode", "sandbox");
        assertEquals(PaymentMode.SANDBOX, Config.paymentMode());

        System.setProperty("qa.paymentMode", "REAL");
        assertEquals(PaymentMode.REAL, Config.paymentMode());
    }

    @Test
    @DisplayName("paymentMode() rejects a value that isn't sandbox or real")
    void paymentMode_rejectsUnrecognizedValue() {
        System.setProperty("qa.paymentMode", "production");
        assertThrows(ConfigValidationException.class, Config::paymentMode);
    }

    private static Properties defaultsWith(String key, String value) {
        Properties properties = new Properties();
        properties.setProperty(key, value);
        return properties;
    }
}
