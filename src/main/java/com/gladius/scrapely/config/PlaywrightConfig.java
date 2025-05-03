package com.gladius.scrapely.config;

import com.microsoft.playwright.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
public class PlaywrightConfig {

    private Playwright playwright;
    private Browser browser;

    @Bean
    public Playwright playwright() {
        this.playwright = Playwright.create();
        return playwright;
    }

    @Bean
    public Browser browser() {
        if (playwright == null) {
            playwright = Playwright.create();
        }

        this.browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(false)
                .setArgs(Arrays.asList(
                        "--disable-blink-features=AutomationControlled",
                        "--window-size=1366,768",
                        "--no-sandbox",
                        "--disable-dev-shm-usage",
                        "--disable-infobars",
                        "--disable-web-security",
                        "--disable-extensions",
                        "--start-maximized"
                )));

        return browser;
    }

    @Bean
    public BrowserContext browserContext(Browser browser) {
        BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(1366, 768)
                .setLocale("en-US")
                .setTimezoneId("Asia/Kolkata")
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")
        );

        // Fix WebDriver
        context.addInitScript("() => {\n" +
                "  Object.defineProperty(navigator, 'webdriver', {\n" +
                "    get: () => false,\n" +
                "  });\n" +
                "}");

        // Chrome object spoofing
        context.addInitScript("window.chrome = { runtime: {}, loadTimes: () => {}, csi: () => {} };");

        // Languages
        context.addInitScript("Object.defineProperty(navigator, 'languages', { get: () => ['en-US', 'en'] });");

        // Plugins as real PluginArray object
        context.addInitScript("""
       (function generatePluginArray() {
                     const pluginData = [
                       { name: "Chrome PDF Plugin", filename: "internal-pdf-viewer", description: "Portable Document Format" },
                       { name: "Chrome PDF Viewer", filename: "mhjfbmdgcfjbbpaeojofohoefgiehjai", description: "" },
                       { name: "Native Client", filename: "internal-nacl-plugin", description: "" },
                     ]
                     const pluginArray = []
                     pluginData.forEach(p => {
                       function FakePlugin () { return p }
                       const plugin = new FakePlugin()
                       Object.setPrototypeOf(plugin, Plugin.prototype);
                       pluginArray.push(plugin)
                     })
                     Object.setPrototypeOf(pluginArray, PluginArray.prototype);
                     return pluginArray
                   })()
    """);

        // Permissions.query Proxy (fix)
        context.addInitScript("""
        const handler = {
            apply: function (target, ctx, args) {
                const param = (args || [])[0];
                if (param && param.name === 'notifications') {
                    const result = { state: Notification.permission };
                    Object.setPrototypeOf(result, PermissionStatus.prototype);
                    return Promise.resolve(result);
                }
                if (param && param.name === 'geolocation') {
                    const result = { state: 'granted' };
                    Object.setPrototypeOf(result, PermissionStatus.prototype);
                    return Promise.resolve(result);
                }
                if (param && param.name === 'camera') {
                    const result = { state: 'granted' };
                    Object.setPrototypeOf(result, PermissionStatus.prototype);
                    return Promise.resolve(result);
                }
                return Reflect.apply(...arguments);
            }
        };

        (function() {
            const originalQuery = window.navigator.permissions.query;
            window.navigator.permissions.query = new Proxy(originalQuery, handler);
        })();
    """);

        // Platform, vendor, concurrency, deviceMemory
        context.addInitScript("Object.defineProperty(navigator, 'platform', { get: () => 'Win32' });");
        context.addInitScript("Object.defineProperty(navigator, 'vendor', { get: () => 'Google Inc.' });");
        context.addInitScript("Object.defineProperty(navigator, 'hardwareConcurrency', { get: () => 8 });");
        context.addInitScript("Object.defineProperty(navigator, 'deviceMemory', { get: () => 8 });");

        // Screen spoofing
        context.addInitScript("screen.width = 1366; screen.height = 768; screen.colorDepth = 24;");

        // WebGL spoofing
        context.addInitScript("""
        const getParameterProxyHandler = {
            apply: function(target, ctx, args) {
                if (args[0] === 37445) return 'Intel Inc.';
                if (args[0] === 37446) return 'Intel(R) Iris(TM) Xe Graphics';
                return Reflect.apply(...arguments);
            }
        };

        const getContextProxyHandler = {
            apply: function(target, ctx, args) {
                const context = Reflect.apply(...arguments);
                if (context && typeof context.getParameter === 'function') {
                    context.getParameter = new Proxy(context.getParameter, getParameterProxyHandler);
                }
                return context;
            }
        };

        HTMLCanvasElement.prototype.getContext = new Proxy(
            HTMLCanvasElement.prototype.getContext,
            getContextProxyHandler
        );
    """);
        context.addInitScript("""
    (function() {
        const originalCanPlayType = HTMLMediaElement.prototype.canPlayType;
        HTMLMediaElement.prototype.canPlayType = function(type) {
            if (typeof type === 'string') {
                if (type.includes('video/mp4') || type.includes('avc1') || type.includes('h264')) {
                    return 'probably';
                }
                if (type.includes('application/vnd.apple.mpegurl') || type.includes('application/x-mpegURL')) {
                    return 'probably';
                }
            }
            return originalCanPlayType.call(this, type);
        };
    })();
""");
        return context;
    }


    @Bean
    public String shutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (browser != null) {
                browser.close();
            }
            if (playwright != null) {
                playwright.close();
            }
        }));
        return null;
    }
}
