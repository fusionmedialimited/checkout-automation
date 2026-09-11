@smoke @qa
Feature: InvestingPro landing page smoke check

  This scenario only confirms the master QA InvestingPro landing page loads. It must not
  create a user or submit a payment.

  Scenario: Landing page loads on master QA
    Given I open the InvestingPro landing page on master QA
    Then the page shows a stable InvestingPro indicator
