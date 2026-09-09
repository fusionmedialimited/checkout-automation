package com.investing.pro.pages;

import com.microsoft.playwright.Page;

/** Base for page objects: locators and raw UI interaction live here, never in step definitions. */
public abstract class BasePage {

    protected final Page page;

    protected BasePage(Page page) {
        this.page = page;
    }
}
