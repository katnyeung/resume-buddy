package com.resumebuddy.jobsearch.infrastructure.external.jobsources;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Infrastructure: Adzuna Web Scraper Service
 * Scrapes full job descriptions from Adzuna redirect URLs
 *
 * Adzuna API returns truncated descriptions ending with "…"
 * This service follows redirect_url to partner sites (nijobs.com, cv-library, totaljobs, reed, etc.)
 * and extracts the full job description using site-specific CSS selectors.
 *
 * Pattern: Same as Reed's two-stage fetch (API search + detail scraping)
 */
@Service
@Slf4j
public class AdzunaWebScraperService {

    @Value("${app.job-crawling.adzuna.scraping.enabled:true}")
    private boolean scrapingEnabled;

    @Value("${app.job-crawling.adzuna.scraping.timeout-seconds:10}")
    private int timeoutSeconds;

    @Value("${app.job-crawling.adzuna.scraping.max-retries:3}")
    private int maxRetries;

    // User-Agent rotation to avoid detection
    private static final List<String> USER_AGENTS = Arrays.asList(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15",
            "Mozilla/5.0 (X11; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/121.0"
    );

    private int userAgentIndex = 0;

    /**
     * Main entry point: Scrape full job description from Adzuna redirect URL
     *
     * @param redirectUrl Adzuna redirect URL (e.g., https://www.adzuna.co.uk/jobs/land/ad/...)
     * @return Full job description HTML, or null if scraping fails
     */
    public String scrapeJobDescription(String redirectUrl) {
        if (!scrapingEnabled) {
            log.debug("Scraping disabled, skipping: {}", redirectUrl);
            return null;
        }

        if (redirectUrl == null || redirectUrl.isEmpty()) {
            log.warn("Empty redirect URL provided");
            return null;
        }

        log.debug("Starting scrape for: {}", redirectUrl);

        // Retry logic with exponential backoff
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                // Step 1: Follow redirect to get final destination URL
                String finalUrl = followRedirect(redirectUrl);
                if (finalUrl == null) {
                    log.warn("Could not follow redirect: {}", redirectUrl);
                    return null;
                }

                log.debug("Final destination URL: {}", finalUrl);

                // Step 2: Fetch HTML from final URL with realistic browser headers
                // IMPORTANT: Jsoup automatically handles gzip decompression
                // DO NOT manually set Accept-Encoding, let Jsoup handle it
                Document doc = Jsoup.connect(finalUrl)
                        .userAgent(getNextUserAgent())
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                        .header("Accept-Language", "en-GB,en;q=0.9,en-US;q=0.8")
                        // .header("Accept-Encoding", "gzip, deflate, br")  // REMOVED - Jsoup adds this automatically
                        .header("DNT", "1")
                        .header("Connection", "keep-alive")
                        .header("Upgrade-Insecure-Requests", "1")
                        .header("Sec-Fetch-Dest", "document")
                        .header("Sec-Fetch-Mode", "navigate")
                        .header("Sec-Fetch-Site", "cross-site")
                        .header("Cache-Control", "max-age=0")
                        .referrer(redirectUrl) // Use Adzuna redirect URL as referrer (looks legitimate)
                        .timeout(timeoutSeconds * 1000)
                        .followRedirects(true)
                        .ignoreContentType(true) // Accept any content type
                        .get();

                // Step 3: Extract description using site-specific selectors
                String description = extractDescriptionFromHtml(doc, finalUrl);

                if (description != null && !description.isEmpty()) {
                    log.debug("Successfully scraped {} chars from {}", description.length(), finalUrl);
                    return description;
                } else {
                    log.warn("No description found at {}", finalUrl);
                    return null;
                }

            } catch (IOException e) {
                log.error("Scraping attempt {}/{} failed: {}", attempt, maxRetries, e.getMessage());

                if (attempt < maxRetries) {
                    // Exponential backoff: 1s, 2s, 4s
                    int delaySec = (int) Math.pow(2, attempt - 1);
                    log.debug("Retrying in {}s...", delaySec);
                    try {
                        Thread.sleep(delaySec * 1000L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("Retry sleep interrupted");
                        return null;
                    }
                }
            } catch (Exception e) {
                log.error("Unexpected error during scraping: {}", e.getMessage(), e);
                return null;
            }
        }

        log.error("All {} scraping attempts failed for: {}", maxRetries, redirectUrl);
        return null;
    }

    /**
     * Extract final destination URL from Adzuna landing page HTML
     *
     * Adzuna uses JavaScript redirect with HTML meta refresh fallback.
     * We parse the HTML to extract the target URL instead of following redirects.
     * This bypasses anti-scraping 403 errors.
     *
     * @param adzunaUrl Adzuna redirect URL
     * @return Final destination URL (totaljobs, nijobs, cv-library, etc.), or null if extraction fails
     */
    private String followRedirect(String adzunaUrl) {
        try {
            // Fetch Adzuna landing page HTML (not the final redirect)
            Document landingPage = Jsoup.connect(adzunaUrl)
                    .userAgent(getNextUserAgent())
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "en-GB,en;q=0.9,en-US;q=0.8")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("DNT", "1")
                    .header("Connection", "keep-alive")
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "none")
                    .header("Cache-Control", "max-age=0")
                    .referrer("https://www.google.com/")
                    .timeout(timeoutSeconds * 1000)
                    .followRedirects(false) // Don't follow redirects, we want the landing page
                    .ignoreHttpErrors(true) // Ignore 403 errors on landing page
                    .get();

            // Strategy 1: Parse meta refresh tag
            // <meta http-equiv="refresh" content="5; url=https://www.totaljobs.com/...">
            Element metaRefresh = landingPage.selectFirst("meta[http-equiv=refresh]");
            if (metaRefresh != null) {
                String content = metaRefresh.attr("content");
                // Extract URL from "5; url=https://..."
                int urlIndex = content.indexOf("url=");
                if (urlIndex != -1) {
                    String finalUrl = content.substring(urlIndex + 4).trim();
                    log.debug("Extracted from meta refresh: {} -> {}", adzunaUrl, finalUrl);
                    return finalUrl;
                }
            }

            // Strategy 2: Parse the "view ad here" link
            // <a href="https://www.totaljobs.com/...">view ad here</a>
            Element viewAdLink = landingPage.selectFirst("a:contains(view ad here)");
            if (viewAdLink != null) {
                String finalUrl = viewAdLink.attr("href");
                if (!finalUrl.isEmpty()) {
                    log.debug("Extracted from 'view ad here' link: {} -> {}", adzunaUrl, finalUrl);
                    return finalUrl;
                }
            }

            // Strategy 3: Parse preconnect link (fastest hint)
            // <link rel="preconnect" href="https://www.totaljobs.com/..." />
            Element preconnectLink = landingPage.selectFirst("link[rel=preconnect]");
            if (preconnectLink != null) {
                String finalUrl = preconnectLink.attr("href");
                if (!finalUrl.isEmpty() && finalUrl.startsWith("http")) {
                    log.debug("Extracted from preconnect: {} -> {}", adzunaUrl, finalUrl);
                    return finalUrl;
                }
            }

            log.warn("Could not extract redirect URL from Adzuna landing page: {}", adzunaUrl);
            return null;

        } catch (IOException e) {
            log.error("Failed to fetch Adzuna landing page {}: {}", adzunaUrl, e.getMessage());
            return null;
        }
    }

    /**
     * Extract job description from HTML using site-specific selectors
     *
     * @param doc Jsoup document
     * @param finalUrl Final destination URL (to determine which site)
     * @return Extracted description HTML, or null if not found
     */
    private String extractDescriptionFromHtml(Document doc, String finalUrl) {
        String description = null;

        // Detect site from URL and use appropriate selectors
        if (finalUrl.contains("totaljobs.com")) {
            description = extractFromTotalJobs(doc);
        } else if (finalUrl.contains("nijobs.com")) {
            description = extractFromNijobs(doc);
        } else if (finalUrl.contains("cv-library.co.uk") || finalUrl.contains("cvlibrary")) {
            description = extractFromCvLibrary(doc);
        } else if (finalUrl.contains("reed.co.uk")) {
            description = extractFromReed(doc);
        } else if (finalUrl.contains("indeed.co.uk") || finalUrl.contains("indeed.com")) {
            description = extractFromIndeed(doc);
        } else {
            log.debug("Unknown site, using generic selectors: {}", finalUrl);
            description = extractGeneric(doc);
        }

        return description;
    }

    /**
     * Extract description from nijobs.com
     */
    private String extractFromNijobs(Element doc) {
        log.debug("Using nijobs.com selectors");

        // Try multiple selectors in order of preference
        Element descElement = doc.selectFirst(".job-description");
        if (descElement == null) {
            descElement = doc.selectFirst(".description-content");
        }
        if (descElement == null) {
            descElement = doc.selectFirst("article");
        }
        if (descElement == null) {
            descElement = doc.selectFirst(".job-details");
        }

        return descElement != null ? descElement.html() : null;
    }

    /**
     * Extract description from cv-library.co.uk
     */
    private String extractFromCvLibrary(Element doc) {
        log.debug("Using cv-library.co.uk selectors");

        Element descElement = doc.selectFirst(".job-description");
        if (descElement == null) {
            descElement = doc.select("#job-description").first();
        }
        if (descElement == null) {
            descElement = doc.selectFirst(".job-details");
        }

        return descElement != null ? descElement.html() : null;
    }

    /**
     * Extract description from totaljobs.com
     * TotalJobs uses Next.js and embeds job data in inline JSON props
     */
    private String extractFromTotalJobs(Element doc) {
        log.debug("Using totaljobs.com selectors");

        // Strategy 1: Extract from Next.js JSON props (primary method)
        // TotalJobs embeds job description in: props.textSections[0].content
        String html = doc.html();

        // Debug: Check if the expected JSON structure exists
        boolean hasTextSections = html.contains("\"textSections\"");
        boolean hasContent = html.contains("\"content\":");
        boolean hasPreloadedState = html.contains("__PRELOADED_STATE__");
        boolean hasNextData = html.contains("__NEXT_DATA__");
        log.debug("TotalJobs HTML check: textSections={}, content={}, __PRELOADED_STATE__={}, __NEXT_DATA__={}, html_length={}",
                  hasTextSections, hasContent, hasPreloadedState, hasNextData, html.length());

        // Temporary debug: Save first 2000 chars to see what we're getting
        if (!hasTextSections && html.length() > 0) {
            String sample = html.substring(0, Math.min(2000, html.length()));
            log.debug("HTML sample (first 2000 chars): {}", sample);
        }

        // Look for: "textSections":[{"content":"<html>..."}]
        // Using regex to find the textSections array
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "\"textSections\"\\s*:\\s*\\[\\s*\\{[^}]*?\"content\"\\s*:\\s*\"(.*?)\"(?=\\s*,\\s*\"title\")",
            java.util.regex.Pattern.DOTALL
        );
        java.util.regex.Matcher matcher = pattern.matcher(html);

        if (matcher.find()) {
            String jsonContent = matcher.group(1);
            log.debug("Found job description in Next.js JSON props ({} chars)", jsonContent.length());

            // Unescape JSON string
            String unescaped = jsonContent
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\/", "/")
                .replace("\\\\", "\\")
                .replace("\\t", "\t")
                .replace("\\r", "\r");

            log.debug("Extracted {} chars from Next.js JSON", unescaped.length());
            return unescaped;
        }

        log.debug("Next.js JSON extraction failed, trying CSS selectors");

        // Strategy 2: Fallback to CSS selectors (legacy or non-Next.js pages)
        Element descElement = doc.selectFirst(".job-description");
        if (descElement == null) {
            descElement = doc.selectFirst("[data-testid='job-description']");
        }
        if (descElement == null) {
            descElement = doc.selectFirst(".description");
        }
        if (descElement == null) {
            descElement = doc.selectFirst("#job-description");
        }
        if (descElement == null) {
            descElement = doc.selectFirst(".job-info");
        }
        if (descElement == null) {
            descElement = doc.selectFirst("section.job-description");
        }
        if (descElement == null) {
            descElement = doc.selectFirst("div[itemprop='description']");
        }

        return descElement != null ? descElement.html() : null;
    }

    /**
     * Extract description from reed.co.uk
     */
    private String extractFromReed(Element doc) {
        log.debug("Using reed.co.uk selectors");

        Element descElement = doc.selectFirst(".description");
        if (descElement == null) {
            descElement = doc.selectFirst(".job-description");
        }

        return descElement != null ? descElement.html() : null;
    }

    /**
     * Extract description from indeed.co.uk/com
     */
    private String extractFromIndeed(Element doc) {
        log.debug("Using indeed selectors");

        Element descElement = doc.selectFirst("#jobDescriptionText");
        if (descElement == null) {
            descElement = doc.selectFirst(".jobsearch-jobDescriptionText");
        }

        return descElement != null ? descElement.html() : null;
    }

    /**
     * Generic fallback extraction for unknown sites
     */
    private String extractGeneric(Element doc) {
        log.debug("Using generic selectors");

        // Try common patterns
        Element descElement = doc.selectFirst("article");
        if (descElement == null) {
            descElement = doc.selectFirst("main");
        }
        if (descElement == null) {
            descElement = doc.selectFirst(".content");
        }
        if (descElement == null) {
            descElement = doc.select("#content").first();
        }
        if (descElement == null) {
            descElement = doc.selectFirst(".job-description");
        }

        return descElement != null ? descElement.html() : null;
    }

    /**
     * Rotate User-Agent to avoid detection
     */
    private String getNextUserAgent() {
        String userAgent = USER_AGENTS.get(userAgentIndex);
        userAgentIndex = (userAgentIndex + 1) % USER_AGENTS.size();
        return userAgent;
    }
}
