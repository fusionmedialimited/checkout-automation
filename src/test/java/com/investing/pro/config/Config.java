package com.investing.pro.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Function;

/**
 * Central configuration access point.
 *
 * <p>Precedence, highest first: JVM system property ({@code -Dqa.baseUrl=...}), environment
 * variable (the key upper-cased with '.' replaced by '_', e.g. {@code QA_BASEURL}), then the
 * checked-in defaults in {@code src/test/resources/config/default.properties}. A key with no
 * value from any source, and no default, fails closed via {@link ConfigValidationException}
 * rather than silently proceeding with an unsafe assumption.
 */
public final class Config {

    private static final String DEFAULTS_RESOURCE = "config/default.properties";
    private static final Properties DEFAULTS = loadDefaults();

    // Test-only seam: lets a test substitute the real OS environment so it isn't affected by
    // whatever QA_* variables happen to be exported in the developer's or CI's actual shell
    // (e.g. from sourcing .env.example) — see useEnvironmentForTesting/resetEnvironmentForTesting
    // below, used by ConfigPrecedenceTest and PaymentSafetyGateTest. Production code always sees
    // System::getenv; nothing here changes runtime behavior outside of tests.
    private static Function<String, String> environment = System::getenv;

    private Config() {
    }

    public static String baseUrl() {
        return get("qa.baseUrl");
    }

    public static PaymentMode paymentMode() {
        return PaymentMode.fromConfigValue(get("qa.paymentMode"));
    }

    public static boolean allowUserCreation() {
        return getBoolean("qa.allowUserCreation");
    }

    public static boolean allowSandboxPurchase() {
        return getBoolean("qa.allowSandboxPurchase");
    }

    public static boolean allowRealCardPurchase() {
        return getBoolean("qa.allowRealCardPurchase");
    }

    public static String realCardAuthorizationRef() {
        return getOrDefault("qa.realCardAuthorizationRef", "");
    }

    public static String browser() {
        return get("qa.browser");
    }

    public static boolean headless() {
        return getBoolean("qa.headless");
    }

    public static int timeoutMs() {
        String value = get("qa.timeoutMs");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new ConfigValidationException(
                    "Invalid integer value for 'qa.timeoutMs': '" + value + "'.", e);
        }
    }

    public static ArtifactPolicy artifactPolicy() {
        return ArtifactPolicy.fromConfigValue(get("qa.artifactPolicy"));
    }

    /** Required value; throws {@link ConfigValidationException} if unset everywhere. */
    public static String get(String key) {
        String value = resolve(key, System::getProperty, environment, DEFAULTS);
        if (value == null || value.isBlank()) {
            throw new ConfigValidationException(
                    "Missing required configuration '" + key + "'. Set it via -D" + key
                            + ", the " + envKey(key) + " environment variable, or "
                            + DEFAULTS_RESOURCE + ".");
        }
        return value;
    }

    public static String getOrDefault(String key, String fallback) {
        String value = resolve(key, System::getProperty, environment, DEFAULTS);
        return (value == null || value.isBlank()) ? fallback : value;
    }

    /**
     * Test-only: substitutes the environment-variable lookup {@link #get}/{@link #getOrDefault}
     * use, so a test exercising the real config methods isn't affected by whatever is actually
     * exported in the process environment. Public (not package-private) because tests in other
     * packages — e.g. {@code PaymentSafetyGateTest}, which exercises this indirectly through
     * {@code PaymentSafetyGate} — need it too. Always pair with
     * {@link #resetEnvironmentForTesting()} (e.g. in an {@code @AfterEach}) so the substitution
     * never leaks into another test.
     */
    public static void useEnvironmentForTesting(Function<String, String> testEnvironment) {
        environment = testEnvironment;
    }

    public static void resetEnvironmentForTesting() {
        environment = System::getenv;
    }

    public static boolean getBoolean(String key) {
        String value = get(key);
        if (value.equalsIgnoreCase("true")) {
            return true;
        }
        if (value.equalsIgnoreCase("false")) {
            return false;
        }
        throw new ConfigValidationException(
                "Invalid boolean value for '" + key + "': '" + value + "'; expected 'true' or 'false'.");
    }

    /**
     * Resolves a single key given explicit lookup functions, so precedence can be unit-tested
     * without mutating real system properties or environment variables.
     */
    static String resolve(String key, Function<String, String> systemProperties,
                           Function<String, String> environment, Properties defaults) {
        String fromProperty = systemProperties.apply(key);
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromProperty.trim();
        }
        String fromEnv = environment.apply(envKey(key));
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        String fromDefaults = defaults.getProperty(key);
        return fromDefaults == null ? null : fromDefaults.trim();
    }

    /** The environment variable name a config key resolves to (public so callers can build
     * accurate run instructions instead of hard-coding the naming rule themselves). */
    public static String envKey(String key) {
        return key.toUpperCase(Locale.ROOT).replace('.', '_');
    }

    private static Properties loadDefaults() {
        Properties properties = new Properties();
        try (InputStream in = Config.class.getClassLoader().getResourceAsStream(DEFAULTS_RESOURCE)) {
            if (in == null) {
                throw new ConfigValidationException("Missing classpath resource " + DEFAULTS_RESOURCE);
            }
            properties.load(in);
        } catch (IOException e) {
            throw new ConfigValidationException("Failed to load " + DEFAULTS_RESOURCE, e);
        }
        return properties;
    }
}
