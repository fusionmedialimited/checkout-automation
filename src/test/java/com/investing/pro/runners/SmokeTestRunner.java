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
 * {@code *Test.java} so {@code mvn test} never picks it up implicitly — invoke it explicitly,
 * see docs/checkout-testing.md for the exact command. This suite must never gain scenarios that
 * create a user or submit a payment; those belong to the future sandbox/real-payment runners.
 *
 * <p>The {@code rerun:} plugin below writes failed-scenario URIs to
 * {@code target/cucumber-rerun/smoke.txt}, which {@code qa-smoke.yml} uses to retry only those
 * scenarios (see docs/checkout-testing.md "Retries"). This is safe here only because nothing in
 * this suite creates a user or submits a payment; a future runner that includes such a scenario
 * must not reuse this rerun-based retry approach without re-reading
 * docs/payment-safety.md "Ambiguous outcomes and retries" first.
 *
 * <p>The Allure plugin writes step/attachment results to {@code target/allure-results} for
 * local/CI report generation only (`mvn allure:report`) — nothing here pushes results to any
 * shared Allure Portal, see docs/architecture.md "Reporting".
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/smoke")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.investing.pro")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@smoke and @qa")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME,
        value = "pretty, html:target/cucumber-report/smoke.html, json:target/cucumber-report/smoke.json, "
                + "rerun:target/cucumber-rerun/smoke.txt, io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm")
public class SmokeTestRunner {
}
