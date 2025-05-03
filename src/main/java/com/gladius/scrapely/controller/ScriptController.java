package com.gladius.scrapely.controller;
import com.gladius.scrapely.model.ScriptRequset;
import com.gladius.scrapely.service.BrowserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/apiv1")
@Slf4j
public class ScriptController {
    //Browser service bean
    private final BrowserService browserService;

    public ScriptController(BrowserService browserService) {
        this.browserService = browserService;
    }

    //checks if the Script controller is working or not. No business logic in this method
    @GetMapping("/stealthtest")
    public ResponseEntity<String> healthcheck() {
        String res = browserService.playwright("https://bot.sannysoft.com");  //https://amiunique.org/
        return new ResponseEntity<>("checking stealth" + "\n" + res, HttpStatus.ACCEPTED);
    }

    //Returns the elements list in String format
    @GetMapping("/open")
    public ResponseEntity<String> getElements(@RequestParam String url) {
        String el = browserService.playwright(url);
        return new ResponseEntity<>("opening " + url + "\n" + el, HttpStatus.ACCEPTED);
    }

    /*
    Important method opens the url and parses the page to return the list of all the elements, CSS selectors and Shadow
    Dom elements which are accessible.

    If the shadow dom is (open) or inside iframe only then the shadow dom elements can be fetched.
     */
    @GetMapping("/elements")
    public ResponseEntity<Map<String, Object>> elementsSel(@RequestParam String url) {
        try {
            Map<String, Object> out = browserService.playwrightv2(url);
            return new ResponseEntity<>(out, HttpStatus.ACCEPTED);
        } catch (Exception e) {
            log.trace("There is an error occured while trying to get the elements of " + url, e);
            return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED).body(null);
        }
    }


    @GetMapping("/scriptflow")
    public ResponseEntity<List<List<String>>> scriptflow(@RequestBody ScriptRequset request) {
        List<List<String>> res = browserService.execute(request);
        return new ResponseEntity<>(res, HttpStatus.ACCEPTED);
    }

    @GetMapping("/search/{q}")
    public  ResponseEntity<byte[]> googlesearch(@PathVariable String q) {
        byte[] res = browserService.googleSearch(q);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"screenshot.png\"")
                .contentType(MediaType.IMAGE_PNG)
                .body(res);
    }

}
