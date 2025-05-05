package com.gladius.scrapely.service;
import com.gladius.scrapely.model.ScriptRequset;
import com.gladius.scrapely.model.SelectorInfo;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@Slf4j
public class BrowserService {
    private final BrowserContext browserContext;

    public BrowserService(BrowserContext browserContext) {
        this.browserContext = browserContext;
    }

    /*
    This method is for opening the urls via playwright and can be removed in future updates since new efficient
    methods are need to be written
    */
    public String playwright(String url) {
        String el = null;
        Page page = null;
        String data = null;
        List<List<String>> splitEls = new ArrayList<>();
        try {
            page = browserContext.newPage();
            Page.NavigateOptions options = new Page.NavigateOptions()
                    .setTimeout(60000)
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED);

            page.navigate(url, options);
            el = page.content();

            // Get elements list safely
            List<List<String>> els = elementList(page);

            // Avoid modifying list during iteration
            for (List<String> stringList : els) {
                for (String string : stringList) {
                    splitEls.add(Arrays.asList(string.split(" ")));
                }
            }

            els.addAll(splitEls);
            data = page.getByText("Stock Screener Data is").allTextContents().toString();// Add the new split elements after iteration

        } catch (PlaywrightException e) {
            log.warn("Pagination stopped at page. No further pages found.", e);
        } catch (Exception e) {
            log.error("Unexpected error occurred during scraping", e);
        } finally {
            if (page != null) {
                data=page.content();  // Ensure page is closed
            }
        }
        return splitEls + "\n\n\tDATA is: " + data;
    }


    //Test method parses the html code for the elements and returns an ArrayList of Elements
    public ArrayList<List<String>> elementList(Page page) {
        ArrayList<List<String>> els = new ArrayList<>();
        // Extract Headings
        List<String> headings = page.locator("h1, h2, h3").allTextContents();
        els.add(headings);
        // Extract Paragraphs
        List<String> paragraphs = page.locator("p").allTextContents();
        els.add(paragraphs);
        // Extract Links
        List<String> links = (List<String>) page.locator("a").evaluateAll("(elements) => elements.map(e => e.href)");
        els.add(links);
        // Extract Buttons
        List<String> buttons = page.locator("button").allTextContents();
        els.add(buttons);
        // Extract Lists (ul, ol)
        List<String> lists = page.locator("ul, ol").allTextContents();
        els.add(lists);
        // Extract Table Data
        List<String> tables = (List<String>) page.locator("table").evaluateAll("(elements) => elements.map(e => e.innerText)");
        els.add(tables);

        //Extract shadow dom data
        List<String> classList = page.locator("ul, ol").allTextContents();
        return els;
    }


    //No usage of this method till now, this method returns the text content of the html page
    public ArrayList<String> selectors(Page page) {
        ArrayList<String> selectors = new ArrayList<>();
        page.locator("a").allTextContents().forEach(el -> {
        });
        return selectors;
    }


    //Returns the list of elements in the JSON Format
    public List<Map<String, Object>> extractSmartSelectors(Page page) {
        return (List<Map<String, Object>>) page.locator("button, input, textarea, select, a, [role], [aria-label], label")
                .evaluateAll("(els) => els.map(e => {\n" +
                        "  try {\n" +
                        "    const tag = e.tagName.toLowerCase();\n" +
                        "    const text = (e.innerText || e.value || '').trim();\n" +
                        "    const label = (e.getAttribute('aria-label') || (e.labels?.[0]?.innerText || '')).trim();\n" +
                        "    const role = e.getAttribute('role') || '';\n" +
                        "    const id = e.id || '';\n" +
                        "    const className = typeof e.className === 'string' ? e.className.trim() : '';\n" +
                        "    const placeholder = (e.getAttribute('placeholder') || '').trim();\n" +
                        "    const name = (e.getAttribute('name') || '').trim();\n" +
                        "    const title = (e.getAttribute('title') || '').trim();\n" +
                        "    const visible = !!(e.offsetWidth || e.offsetHeight || e.getClientRects().length);\n" +

                        "    let selectorType = 'css';\n" +
                        "    let selectorValue = '';\n" +

                        "    if (label) {\n" +
                        "      selectorType = 'label';\n" +
                        "      selectorValue = label;\n" +
                        "    } else if (role && text) {\n" +
                        "      selectorType = 'role';\n" +
                        "      selectorValue = text;\n" +
                        "    } else if (text) {\n" +
                        "      selectorType = 'text';\n" +
                        "      selectorValue = text;\n" +
                        "    } else if (placeholder) {\n" +
                        "      selectorType = 'placeholder';\n" +
                        "      selectorValue = placeholder;\n" +
                        "    } else if (id) {\n" +
                        "      selectorValue = `#${id}`;\n" +
                        "    } else if (name) {\n" +
                        "      selectorValue = `[name='${name}']`;\n" +
                        "    } else if (title) {\n" +
                        "      selectorValue = `[title='${title}']`;\n" +
                        "    }\n" +

                        "    return {\n" +
                        "      tag,\n" +
                        "      text,\n" +
                        "      label,\n" +
                        "      role,\n" +
                        "      id,\n" +
                        "      class: className,\n" +
                        "      name,\n" +
                        "      title,\n" +
                        "      placeholder,\n" +
                        "      visible,\n" +
                        "      suggestedSelectorType: selectorType,\n" +
                        "      selector: selectorValue\n" +
                        "    };\n" +
                        "  } catch (err) {\n" +
                        "    return { error: err.message || 'Unknown error during selector extraction' };\n" +
                        "  }\n" +
                        "})"
                );
    }


    public List<Map<String, Object>> extractAllSelectorsWithShadowSupport(Page page) {
        return (List<Map<String, Object>>) page.evaluate(
"(() => {\n" +
        "  function extractElements(root) {\n" +
        "    const elements = [];\n" +
        "    function traverse(node) {\n" +
        "      const treeWalker = document.createTreeWalker(\n" +
        "        node,\n" +
        "        NodeFilter.SHOW_ELEMENT,\n" +
        "        null,\n" +
        "        false\n" +
        "      );\n" +
        "      let currentNode = treeWalker.currentNode;\n" +
        "      while (currentNode) {\n" +
        "        elements.push(currentNode);\n" +
        "        if (currentNode.shadowRoot) {\n" +
        "          traverse(currentNode.shadowRoot);\n" +
        "        }\n" +
        "        currentNode = treeWalker.nextNode();\n" +
        "      }\n" +
        "    }\n" +
        "    traverse(root);\n" +
        "    return elements;\n" +
        "  }\n" +
        "\n" +
        "  const allElements = extractElements(document);\n" +
        "\n" +
        "  function getSuggestedAction(tag) {\n" +
        "    const fillTags = ['input', 'textarea'];\n" +
        "    const clickTags = ['button', 'a', 'input', 'summary'];\n" +
        "    const selectTags = ['select'];\n" +
        "\n" +
        "    if (fillTags.includes(tag)) return 'fill';\n" +
        "    if (selectTags.includes(tag)) return 'select';\n" +
        "    if (clickTags.includes(tag)) return 'click';\n" +
        "    return 'click';\n" +
        "  }\n" +
        "\n" +
        "  return allElements.map(e => {\n" +
        "    if (!(e instanceof Element)) return null;\n" +
        "\n" +
        "    const tag = e.tagName.toLowerCase();\n" +
        "    const text = e.innerText?.trim() || e.value || '';\n" +
        "    const label = e.getAttribute('aria-label') || (e.labels && e.labels.length > 0 ? e.labels[0].innerText.trim() : '');\n" +
        "    const role = e.getAttribute('role') || '';\n" +
        "    const id = e.id || '';\n" +
        "    const className = typeof e.className === 'string' ? e.className.trim().replace(/\\s+/g, '.') : '';\n" +
        "    const placeholder = e.getAttribute('placeholder') || '';\n" +
        "    const type = e.getAttribute('type') || '';\n" +
        "\n" +
        "    let selector = '';\n" +
        "    let selectorType = '';\n" +
        "    let selectorCode = '';\n" +
        "\n" +
        "    if (role && text) {\n" +
        "      selectorType = 'getByRole';\n" +
        "      selector = role;\n" +
        "      selectorCode = `getByRole('${role}', { name: '${text}' })`;\n" +
        "    } else if (label) {\n" +
        "      selectorType = 'getByLabel';\n" +
        "      selector = label;\n" +
        "      selectorCode = `getByLabel('${label}')`;\n" +
        "    } else if (text) {\n" +
        "      selectorType = 'getByText';\n" +
        "      selector = text;\n" +
        "      selectorCode = `getByText('${text}', { exact: true })`;\n" +
        "    } else if (placeholder) {\n" +
        "      selectorType = 'placeholder';\n" +
        "      selector = placeholder;\n" +
        "      selectorCode = `locator('input[placeholder=\"${placeholder}\"]')`;\n" +
        "    } else if (id) {\n" +
        "      selectorType = 'css';\n" +
        "      selector = `#${id}`;\n" +
        "      selectorCode = `locator('${selector}')`;\n" +
        "    } else {\n" +
        "      selectorType = 'css';\n" +
        "      selector = tag + (className ? '.' + className : '');\n" +
        "      selectorCode = `locator('${selector}')`;\n" +
        "    }\n" +
        "\n" +
        "    return {\n" +
        "      tag,\n" +
        "      type,\n" +
        "      text,\n" +
        "      label,\n" +
        "      role,\n" +
        "      id,\n" +
        "      class: className,\n" +
        "      placeholder,\n" +
        "      suggestedSelectorType: selectorType,\n" +
        "      selectorValue: selector,\n" +
        "      playwrightSelector: selectorCode,\n" +
        "      suggestedAction: getSuggestedAction(tag)\n" +
        "    };\n" +
        "  }).filter(e => e !== null && e.suggestedSelectorType !== '');\n" +
        "})();\n"

        );
    }



    //opens the url and returns the page html
    public Map<String, Object> playwrightv2(String url) {
        Map<String, Object> result = new LinkedHashMap<>();
        Page page = null;

        try {
            page = browserContext.newPage();
            Page.NavigateOptions options = new Page.NavigateOptions()
                    .setTimeout(60000)
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED);

            page.navigate(url, options);

            result.put("smartSelectors", extractAllSelectorsWithShadowSupport(page));
            String link= (String) page.evaluate("const links = document.links" +"\n" +
                    "return links");
            result.put("links",link);
        } catch (Exception e) {
            log.error("Error in playwrightv2", e);
        } finally {
            if (page != null) page.close();
        }

        return result;
    }



    public List<List<String>> execute(ScriptRequset request) {
        Page page = browserContext.newPage();
        Page.NavigateOptions options = new Page.NavigateOptions()
                .setTimeout(60000)
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED);

        page.navigate(request.getUrl(), options);
        for (SelectorInfo s : request.getSelectors()) {
//            if (s.getWaitForSelector() != null && !s.getWaitForSelector().isEmpty()) {
//                page.waitForSelector(s.getWaitForSelector());
//            }

            Locator locator = getLocator(page, s);

            switch (s.getAction().toLowerCase()) {
                case "click":
                    locator.click();
                    break;
                case "fill":
                    locator.fill(s.getValue() != null ? s.getValue() : "");
                    break;
                case "hover":
                    locator.hover();
                    break;
                case "gettext":
                    String text = locator.innerText();
                    System.out.println("Extracted Text: " + text);
                    break;
                case "check":
                    locator.check();
                    break;
                case "uncheck":
                    locator.uncheck();
                    break;
                default:
                    System.out.println("Unsupported action: " + s.getAction());
            }
        }


        List<List<String>> allData = new ArrayList<>();

        // Now handle pagination and extraction
        SelectorInfo extractSelector = request.getSelectors().stream()
                .filter(s -> "extract".equalsIgnoreCase(s.getAction()))
                .findFirst().orElse(null);

        if (extractSelector != null) {
            try {
                page.waitForSelector(extractSelector.getWaitForSelector());

                for (int i = 1; i <= 8; i++) {
                    try {
                        Locator rows = page.locator(extractSelector.getSelector());
                        int rowCount = rows.count();

                        for (int j = 0; j < rowCount; j++) {
                            Locator row = rows.nth(j);
                            Locator cells = row.locator("td");
                            int cellCount = cells.count();

                            List<String> rowData = new ArrayList<>();
                            for (int k = 0; k < cellCount; k++) {
                                String cellText = cells.nth(k).innerText().trim();
                                rowData.add(cellText);
                            }

                            allData.add(rowData);
                        }

                        Thread.sleep(1000 + (long) (Math.random() * 6000));

                        if (i < 8) {
                            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                                    .setName(String.valueOf(i + 1)).setExact(true)).click();

                            page.waitForSelector(extractSelector.getWaitForSelector());
                        }
                    } catch (PlaywrightException e) {
                        log.warn("Pagination stopped at page {}. No further pages found.", i);
                        break;
                    }
                }
            } catch (Exception e) {
                log.error("Error while extracting data: {}", e.getMessage(), e);
            }
        }

        return allData;

    }


    private Locator getLocator(Page page, SelectorInfo sa) {
        String selector = sa.getSelector();
        switch (sa.getSelectorType().toLowerCase()) {
            case "label":
                return page.getByLabel(selector,new Page.GetByLabelOptions().setExact(true));
            case "role":
                return page.getByRole(AriaRole.valueOf(selector.toUpperCase()), new Page.GetByRoleOptions().setName(sa.getValue()));

            case "text":
                return page.getByText(selector);
            case "placeholder":
                return page.getByPlaceholder(selector);
            case "alttext":
                return page.getByAltText(selector);
            case "css":
            default:
                return page.locator(selector);
        }
    }



    public byte[] googleSearch(String query) {
        Page page = null;
        StringBuilder result = new StringBuilder();
        Random random = new Random();

        byte[] screenshot = null;
        try {
            page = browserContext.newPage();

            page.navigate("https://www.google.com",
                    new Page.NavigateOptions()
                            .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                            .setTimeout(60000)
            );

            Thread.sleep(1000 + random.nextInt(1500)); // Wait for possible interstitial

            // Handle consent & "Stay signed out"
            try {
                Locator staySignedOut = page.locator("button:has-text('Stay signed out')");
                if (staySignedOut.isVisible()) {
                    staySignedOut.click();
                    Thread.sleep(1000 + random.nextInt(1500));
                }

                Locator acceptButton = page.locator("button:has-text('I agree'), button:has-text('Accept all')");
                if (acceptButton.isVisible()) {
                    acceptButton.click();
                    Thread.sleep(1000 + random.nextInt(1500));
                }
            } catch (Exception ignored) {
            }

            // Locate search box
            page.getByLabel("Search", new Page.GetByLabelOptions().setExact(true)).fill(query);
            ;


            Thread.sleep(500 + random.nextInt(1000));
            page.keyboard().press("Enter");


            Thread.sleep(2500 + random.nextInt(5000));


        } catch (Exception e) {
            log.error("Error during Google search", e);
            result.append("Error: ").append(e.getMessage());
        } finally {
            if (page != null) {
                screenshot = page.screenshot(new Page.ScreenshotOptions().setFullPage(true));
            }

        }


        return screenshot;
    }

}
