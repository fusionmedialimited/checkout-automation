package com.investing.pro.pages;

import com.microsoft.playwright.Page;

/**
 * Base for page objects: locators and raw UI interaction (navigation, clicks, typing) live here,
 * never in step definitions. Assertions are the opposite: they belong in step definitions, not
 * here — a page object exposes what a step needs to assert against ({@link #page()}, its own
 * public locators), it doesn't assert anything itself.
 */
public abstract class BasePage {

    protected final Page page;

    protected BasePage(Page page) {
        this.page = page;
    }

    /** The underlying Playwright page, for a step definition to run assertions against. */
    public Page page() {
        return page;
    }
}
