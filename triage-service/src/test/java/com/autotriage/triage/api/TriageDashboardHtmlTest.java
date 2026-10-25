package com.autotriage.triage.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression guard for stored XSS in the triage dashboard: finding fields such as filePath come from scanned
 * repositories, so the page must only ever insert them as text.
 */
class TriageDashboardHtmlTest {

    private static final String DASHBOARD = "META-INF/resources/triage/index.html";
    private static final Pattern HTML_SINKS = Pattern.compile("innerHTML|outerHTML|insertAdjacentHTML|document\\.write");

    @Test
    void dashboardDoesNotWriteFindingDataAsHtml() throws IOException {
        String html = readDashboard();

        assertFalse(HTML_SINKS.matcher(html).find(), "dashboard must not use HTML string sinks for finding data");
        assertTrue(html.contains("textContent"), "finding cells should be rendered with textContent");
    }

    @Test
    void dashboardEncodesFindingIdInActionUrls() throws IOException {
        String html = readDashboard();

        assertFalse(html.contains("/triage/findings/${finding.id}"), "finding id must be URL-encoded");
        assertTrue(html.contains("encodeURIComponent(finding.id)"));
    }

    private static String readDashboard() throws IOException {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(DASHBOARD)) {
            assertNotNull(in, "dashboard resource not found: " + DASHBOARD);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
