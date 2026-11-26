/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck;

import org.graalvm.internal.tck.model.MetadataVersionsIndexEntry;
import org.gradle.api.GradleException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Renders and verifies versioned artifact URLs before a pre-release metadata entry is promoted.
/// §FS-metadata.1 §AR-test-harness.5
record PromotedArtifactUrls(String sourceCodeUrl, String testCodeUrl, String documentationUrl) {
    private static final String VERSION_PLACEHOLDER = "$version$";
    private static final Duration URL_VERIFICATION_TIMEOUT = Duration.ofSeconds(20);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(URL_VERIFICATION_TIMEOUT)
            .build();
    private static final Pattern URL_VERSION_TOKEN_PATTERN = Pattern.compile(
            "(?i)(?<![A-Za-z0-9])\\d+(?:\\.\\d+)+(?:\\.Final|\\.RELEASE)?(?:[-.](?:alpha\\d*|beta\\d*|rc\\d*|cr\\d*|m\\d+|ea\\d*|b\\d+|\\d+|preview))?(?![A-Za-z0-9])"
    );

    static PromotedArtifactUrls from(MetadataVersionsIndexEntry entry, String oldVersion, String newVersion) {
        return new PromotedArtifactUrls(
                promoteUrl("source-code-url", entry.sourceCodeUrl(), oldVersion, newVersion),
                promoteUrl("test-code-url", entry.testCodeUrl(), oldVersion, newVersion),
                promoteUrl("documentation-url", entry.documentationUrl(), oldVersion, newVersion)
        );
    }

    private static String promoteUrl(String fieldName, String currentUrl, String oldVersion, String newVersion) {
        if (!hasText(currentUrl) || "N/A".equals(currentUrl)) {
            return currentUrl;
        }

        String promotedUrl = renderVersionTemplate(currentUrl, oldVersion, newVersion);
        verifyPromotedUrl(fieldName, promotedUrl, oldVersion, newVersion);
        return toStoredVersionTemplate(promotedUrl, newVersion);
    }

    private static void verifyPromotedUrl(String fieldName, String promotedUrl, String oldVersion, String newVersion) {
        if (!promotedUrl.contains(newVersion)) {
            throw staleUrlException(fieldName, promotedUrl, oldVersion, newVersion, "URL does not contain the promoted metadata version");
        }
        if (containsIgnoreCase(promotedUrl, oldVersion)) {
            throw staleUrlException(fieldName, promotedUrl, oldVersion, newVersion, "URL still contains the old pre-release version");
        }

        try {
            verifyUrlResolves(promotedUrl);
        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw staleUrlException(fieldName, promotedUrl, oldVersion, newVersion, e.getMessage());
        }
    }

    private static String renderVersionTemplate(String currentUrl, String oldVersion, String newVersion) {
        if (currentUrl.contains(VERSION_PLACEHOLDER)) {
            return currentUrl.replace(VERSION_PLACEHOLDER, newVersion);
        }
        if (containsIgnoreCase(currentUrl, oldVersion)) {
            Pattern oldVersionPattern = Pattern.compile(Pattern.quote(oldVersion), Pattern.CASE_INSENSITIVE);
            return oldVersionPattern.matcher(currentUrl).replaceAll(Matcher.quoteReplacement(newVersion));
        }

        String urlVersion = findTemplateVersionToken(currentUrl, newVersion);
        if (urlVersion == null) {
            return currentUrl;
        }
        Pattern urlVersionPattern = Pattern.compile(Pattern.quote(urlVersion), Pattern.CASE_INSENSITIVE);
        return urlVersionPattern.matcher(currentUrl).replaceAll(Matcher.quoteReplacement(newVersion));
    }

    private static String findTemplateVersionToken(String currentUrl, String newVersion) {
        Matcher newVersionMatcher = TestedVersionUpdaterTask.VERSION_PATTERN.matcher(newVersion);
        if (!newVersionMatcher.matches() || newVersionMatcher.group(2) != null) {
            return null;
        }

        String baseVersion = newVersionMatcher.group(1);
        Matcher urlVersionMatcher = URL_VERSION_TOKEN_PATTERN.matcher(currentUrl);
        while (urlVersionMatcher.find()) {
            String urlVersion = urlVersionMatcher.group();
            if (urlVersion.equalsIgnoreCase(newVersion)) {
                continue;
            }

            Matcher candidateMatcher = TestedVersionUpdaterTask.VERSION_PATTERN.matcher(urlVersion);
            if (candidateMatcher.matches() && candidateMatcher.group(2) != null && baseVersion.equals(candidateMatcher.group(1))) {
                return urlVersion;
            }
        }
        return null;
    }

    private static String toStoredVersionTemplate(String url, String version) {
        Pattern versionPattern = Pattern.compile(Pattern.quote(version), Pattern.CASE_INSENSITIVE);
        return versionPattern.matcher(url).replaceAll(Matcher.quoteReplacement(VERSION_PLACEHOLDER));
    }

    private static boolean containsIgnoreCase(String value, String token) {
        return value.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT));
    }

    private static GradleException staleUrlException(String fieldName, String promotedUrl, String oldVersion, String newVersion, String reason) {
        return new GradleException(
                "Cannot promote " + fieldName + " from " + oldVersion + " to " + newVersion + ". " +
                        "Candidate URL failed verification: " + promotedUrl + ". Reason: " + reason
        );
    }

    private static void verifyUrlResolves(String url) throws IOException, InterruptedException {
        HttpResponse<Void> response = sendRequest(url, "HEAD", HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() == 405 || response.statusCode() == 403) {
            response = sendRequest(url, "GET", HttpResponse.BodyHandlers.discarding());
        }
        verifySuccessStatus(url, response.statusCode());
    }

    private static <T> HttpResponse<T> sendRequest(String url, String method, HttpResponse.BodyHandler<T> bodyHandler) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(URL_VERIFICATION_TIMEOUT)
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
        return HTTP_CLIENT.send(request, bodyHandler);
    }

    private static void verifySuccessStatus(String url, int statusCode) throws IOException {
        if (statusCode < 200 || statusCode >= 300) {
            throw new IOException("URL did not resolve successfully: HTTP " + statusCode + " for " + url);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}
