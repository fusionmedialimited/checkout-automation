@smoke @qa
Feature: InvestingPro landing page smoke check

  This scenario only confirms the configured QA InvestingPro landing page loads (master QA by
  default — see qa.baseUrl / the qa-smoke workflow's environment input). It must not create a
  user or submit a payment.

  Scenario: Landing page loads on the configured QA target
    Given I open the InvestingPro landing page on the configured QA target
    Then the page shows a stable InvestingPro indicator
