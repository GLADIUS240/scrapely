package com.gladius.scrapely.model;

import lombok.Data;

@Data
public class SelectorInfo {
    private String tag;
    private String selectorType;
    private String selector;
    private String action;
    private String value;
    private String waitForSelector;
}
