package com.investing.pro.runners;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * Runs the QA smoke scenarios only (tagged {@code @smoke @qa}). Deliberately not named
 * {@code *Test.java} so {@code ./gradlew test} never picks it up implicitly — invoke it via the
 * dedicated {@code qaSmokeTest} Gradle task, see docs/checkout-testing.md for the exact command.
 * This suite must never gain scenarios that create a user or submit a payment; those belong to
 * the future sandbox/real-payment runners.
 *
 * <p>The {@code rerun:} plugin below writes failed-scenario URIs to
 * {@code build/cucumber-rerun/smoke.txt}. {@code qa-smoke.yml} uses this to retry only those
 * scenarios — passed back in as a comma-joined list of URIs via {@code -Dcucumber.features=...}
 * (never the {@code @file} syntax: that is a Cucumber-CLI-only convention and is silently
 * ignored by the JUnit Platform engine's {@code cucumber.features} configuration parameter,
 * which would make a "retry" actually run zero scenarios and falsely report success — confirmed
 * empirically, not assumed). This rerun approach is safe here only because nothing in this suite
 * creates a user or submits a payment; a future runner that includes such a scenario must not
 * reuse it without re-reading docs/payment-safety.md "Ambiguous outcomes and retries" first.
 *
 * <p>The Allure plugin writes step/attachment results to {@code build/allure-results} and is
 * this suite's canonical report — Cucumber's own {@code html:}/{@code json:} plugins are
 * deliberately not registered here, so there is exactly one BDD report format instead of two
 * that could drift out of sync. {@code pretty} stays for human-readable console output during a
 * run. Unit tests are untouched by this: they keep plain JUnit XML reports, since running
 * {@code allure-junit5} in the same JVM as the Cucumber JUnit-Platform engine (as this Suite
 * class does) risks it also instrumenting the Suite's own JUnit-Platform-engine node, producing
 * duplicate/conflicting Allure results for the same scenario — see docs/architecture.md
 * "Reporting".
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/smoke")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.investing.pro")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@smoke and @qa")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME,
        value = "pretty, rerun:build/cucumber-rerun/smoke.txt, io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm")
public class SmokeTestRunner {
}
