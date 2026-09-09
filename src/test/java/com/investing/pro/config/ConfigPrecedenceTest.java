package com.investing.pro.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigPrecedenceTest {

    private static final String KEY = "qa.someSetting";

    @AfterEach
    void clearRealSystemProperties() {
        System.clearProperty(KEY);
        System.clearProperty("qa.baseUrl");
        System.clearProperty("qa.allowUserCreation");
        System.clearProperty("qa.paymentMode");
        System.clearProperty("qa.timeoutMs");
    }

    @Test
    void resolve_prefersSystemPropertyOverEnvAndDefault() {
        Properties defaults = defaultsWith(KEY, "from-default");
        String result = Config.resolve(KEY,
                key -> "from-property",
                key -> "from-env",
                defaults);

        assertEquals("from-property", result);
    }

    @Test
    void resolve_prefersEnvironmentOverDefault_whenNoSystemProperty() {
        Properties defaults = defaultsWith(KEY, "from-default");
        String result = Config.resolve(KEY,
                key -> null,
                key -> "from-env",
                defaults);

        assertEquals("from-env", result);
    }

    @Test
    void resolve_fallsBackToDefault_whenNothingElseSet() {
        Properties defaults = defaultsWith(KEY, "from-default");
        String result = Config.resolve(KEY, key -> null, key -> null, defaults);

        assertEquals("from-default", result);
    }

    @Test
    void resolve_ignoresBlankSystemPropertyAndEnvironmentValues() {
        Properties defaults = defaultsWith(KEY, "from-default");
        String result = Config.resolve(KEY, key -> "  ", key -> "", defaults);

        assertEquals("from-default", result);
    }

    @Test
    void resolve_returnsNull_whenNoSourceHasAValue() {
        String result = Config.resolve(KEY, key -> null, key -> null, new Properties());
        assertNull(result);
    }

    @Test
    void resolve_trimsSurroundingWhitespaceFromTheResolvedValue() {
        Properties defaults = defaultsWith(KEY, "from-default");
        assertEquals("from-property", Config.resolve(KEY, key -> " from-property ", key -> null, defaults));
        assertEquals("from-env", Config.resolve(KEY, key -> null, key -> " from-env ", defaults));
        assertEquals("from-default", Config.resolve(KEY, key -> null, key -> null, defaultsWith(KEY, " from-default ")));
    }

    @Test
    void envKey_upperCasesAndReplacesDotsWithUnderscores() {
        assertEquals("QA_ALLOW_USER_CREATION", Config.envKey("qa.allow.user.creation"));
    }

    @Test
    void get_throwsConfigValidationException_whenKeyIsMissingEverywhere() {
        ConfigValidationException exception = assertThrows(ConfigValidationException.class,
                () -> Config.get("qa.thisKeyDoesNotExistAnywhere"));

        assertTrue(exception.getMessage().contains("qa.thisKeyDoesNotExistAnywhere"));
    }

    @Test
    void baseUrl_returnsCheckedInDefault_whenNotOverridden() {
        assertEquals("https://master--www.ams-qa.finboxgcp.investing.com/pro/", Config.baseUrl());
    }

    @Test
    void allowUserCreation_defaultsToFalse() {
        assertEquals(false, Config.allowUserCreation());
    }

    @Test
    void allowUserCreation_canBeOverriddenBySystemProperty() {
        System.setProperty("qa.allowUserCreation", "true");
        assertTrue(Config.allowUserCreation());
    }

    @Test
    void getBoolean_toleratesSurroundingWhitespace() {
        System.setProperty("qa.allowUserCreation", " true ");
        assertTrue(Config.allowUserCreation());
    }

    @Test
    void getBoolean_rejectsValuesThatAreNotExactlyTrueOrFalse() {
        System.setProperty("qa.allowUserCreation", "treu");
        assertThrows(ConfigValidationException.class, Config::allowUserCreation);
    }

    @Test
    void timeoutMs_returnsCheckedInDefault_whenNotOverridden() {
        assertEquals(30000, Config.timeoutMs());
    }

    @Test
    void timeoutMs_rejectsNonIntegerValue_withConfigValidationExceptionNotNumberFormatException() {
        System.setProperty("qa.timeoutMs", "not-a-number");
        assertThrows(ConfigValidationException.class, Config::timeoutMs);
    }

    @Test
    void paymentMode_hasNoDefault_andFailsClosed() {
        assertThrows(ConfigValidationException.class, Config::paymentMode);
    }

    @Test
    void paymentMode_parsesConfiguredSandboxAndReal() {
        System.setProperty("qa.paymentMode", "sandbox");
        assertEquals(PaymentMode.SANDBOX, Config.paymentMode());

        System.setProperty("qa.paymentMode", "REAL");
        assertEquals(PaymentMode.REAL, Config.paymentMode());
    }

    @Test
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
