package com.gladius.scrapely.model;

import lombok.Data;

import java.util.HashMap;
import java.util.List;

@Data
public class ScriptRequset {

    private String url;
    private List<SelectorInfo> selectors;
    private String action;

}
