package com.lin.spring.apiprotect.service;

import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
public class HtmlSanitizerService {

    public String sanitize(String input) {
        if (input == null) {
            return null;
        }
        return HtmlUtils.htmlEscape(input.trim());
    }
}
