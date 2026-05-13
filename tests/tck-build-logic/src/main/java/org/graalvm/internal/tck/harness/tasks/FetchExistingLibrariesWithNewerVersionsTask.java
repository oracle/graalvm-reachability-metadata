/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.graalvm.internal.tck.TestedVersionUpdaterTask;
import org.graalvm.internal.tck.model.MetadataVersionsIndexEntry;
import org.graalvm.internal.tck.model.SkippedVersionEntry;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.util.internal.VersionNumber;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public abstract class FetchExistingLibrariesWithNewerVersionsTask extends DefaultTask {

    @Input
    public abstract ListProperty<String> getAllLibraryCoordinates();

    private static final List<String> INFRASTRUCTURE_TESTS = List.of("samples", "org.example");
    private static final String MAVEN_CENTRAL_BASE_URL = "https://repo.maven.apache.org/maven2";
    private static final int MAX_METADATA_READ_ATTEMPTS = 5;
    private static final Duration MIN_REMOTE_REQUEST_INTERVAL = Duration.ofMillis(250);
    private static final Duration DEFAULT_RETRY_DELAY = Duration.ofSeconds(2);
    private static final Duration MAX_RETRY_DELAY = Duration.ofSeconds(30);
    private static final int CONNECTION_TIMEOUT_MILLIS = (int) Duration.ofSeconds(30).toMillis();
    private static final String USER_AGENT = "graalvm-reachability-metadata-new-library-version-checker";
    private static Instant nextRemoteRequest = Instant.EPOCH;

    @TaskAction
    public void action() {
        Set<String> libraries = new LinkedHashSet<>();
        for (String coord : getAllLibraryCoordinates().get()) {
            int last = coord.lastIndexOf(':');
            if (last > 0) {
                libraries.add(coord.substring(0, last));
            }
        }

        List<String> newerVersions = new ArrayList<>();
        for (String libraryName : libraries) {
            if (INFRASTRUCTURE_TESTS.stream().noneMatch(libraryName::startsWith)) {
                List<String> versions = getNewerVersionsFor(libraryName, getLatestLibraryVersion(libraryName));
                List<String> skipped = getSkippedVersions(libraryName);
                versions.removeAll(skipped);
                for (String v : versions) {
                    newerVersions.add(libraryName + ":" + v);
                }
            }
        }

        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (String coord : newerVersions) {
            String[] parts = coord.split(":", -1);
            String key = parts[0] + ":" + parts[1];
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(parts[2]);
        }

        List<Map<String, Object>> pairs = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : grouped.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", e.getKey());
            m.put("versions", e.getValue());
            pairs.add(m);
        }

        try {
            ObjectMapper om = new ObjectMapper()
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL);
            System.out.println(om.writeValueAsString(pairs));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static List<String> getNewerVersionsFor(String library, String startingVersion) {
        String[] libraryParts = library.split(":");
        String group = libraryParts[0].replace(".", "/");
        String artifact = libraryParts[1];
        Optional<String> metadata = readMavenMetadata(
                MAVEN_CENTRAL_BASE_URL + "/" + group + "/" + artifact + "/maven-metadata.xml");
        if (metadata.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> newerVersions = getNewerVersionsFromLibraryIndex(metadata.get(), startingVersion, library);

        List<String> testedVersions = getTestedVersions(library);
        newerVersions.removeAll(testedVersions);

        return filterPreReleases(newerVersions);
    }

    static Optional<String> readMavenMetadata(String metadataUrl) {
        return readMavenMetadata(metadataUrl, millis -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting to retry Maven metadata request", e);
            }
        });
    }

    static Optional<String> readMavenMetadata(String metadataUrl, MetadataReadSleeper sleeper) {
        URI metadataUri = URI.create(metadataUrl);
        try {
            for (int attempt = 1; attempt <= MAX_METADATA_READ_ATTEMPTS; attempt++) {
                try {
                    return readMavenMetadataOnce(metadataUri);
                } catch (RetryableMetadataReadException e) {
                    if (attempt == MAX_METADATA_READ_ATTEMPTS) {
                        throw new IOException("Failed to read Maven metadata after " + MAX_METADATA_READ_ATTEMPTS
                                + " attempts from " + metadataUrl + ": " + e.getMessage(), e);
                    }
                    sleeper.sleep(retryDelay(e, attempt).toMillis());
                }
            }
            throw new IOException("Failed to read Maven metadata from " + metadataUrl);
        } catch (FileNotFoundException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Optional<String> readMavenMetadataOnce(URI metadataUri) throws IOException {
        URLConnection connection = metadataUri.toURL().openConnection();
        connection.setConnectTimeout(CONNECTION_TIMEOUT_MILLIS);
        connection.setReadTimeout(CONNECTION_TIMEOUT_MILLIS);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        HttpURLConnection httpConnection = connection instanceof HttpURLConnection http ? http : null;
        try {
            if (httpConnection != null) {
                throttleRemoteRequest();
                int responseCode = httpConnection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    return Optional.empty();
                }
                if (isRetryableResponse(responseCode)) {
                    throw new RetryableMetadataReadException(
                            "HTTP " + responseCode + " for URL: " + metadataUri,
                            retryAfter(httpConnection));
                }
                if (responseCode >= HttpURLConnection.HTTP_BAD_REQUEST) {
                    throw new IOException("Server returned HTTP response code: " + responseCode
                            + " for URL: " + metadataUri);
                }
            }
            return Optional.of(new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        } finally {
            if (httpConnection != null) {
                httpConnection.disconnect();
            }
        }
    }

    private static synchronized void throttleRemoteRequest() {
        Instant now = Instant.now();
        if (now.isBefore(nextRemoteRequest)) {
            sleepUnchecked(Duration.between(now, nextRemoteRequest));
        }
        nextRemoteRequest = Instant.now().plus(MIN_REMOTE_REQUEST_INTERVAL);
    }

    private static void sleepUnchecked(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while throttling Maven metadata requests", e);
        }
    }

    private static boolean isRetryableResponse(int responseCode) {
        return responseCode == 429
                || responseCode == HttpURLConnection.HTTP_INTERNAL_ERROR
                || responseCode == HttpURLConnection.HTTP_BAD_GATEWAY
                || responseCode == HttpURLConnection.HTTP_UNAVAILABLE
                || responseCode == HttpURLConnection.HTTP_GATEWAY_TIMEOUT;
    }

    private static Optional<Duration> retryAfter(HttpURLConnection connection) {
        String retryAfterHeader = connection.getHeaderField("Retry-After");
        if (retryAfterHeader == null || retryAfterHeader.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Duration.ofSeconds(Long.parseLong(retryAfterHeader)));
        } catch (NumberFormatException e) {
            try {
                Instant retryAt = DateTimeFormatter.RFC_1123_DATE_TIME.parse(retryAfterHeader, Instant::from);
                return Optional.of(Duration.between(Instant.now(), retryAt));
            } catch (DateTimeParseException ignored) {
                return Optional.empty();
            }
        }
    }

    private static Duration retryDelay(RetryableMetadataReadException e, int attempt) {
        Duration delay = e.retryAfter().orElse(DEFAULT_RETRY_DELAY.multipliedBy(1L << (attempt - 1)));
        if (delay.isNegative() || delay.isZero()) {
            return DEFAULT_RETRY_DELAY;
        }
        if (delay.compareTo(MAX_RETRY_DELAY) > 0) {
            return MAX_RETRY_DELAY;
        }
        return delay;
    }

    @FunctionalInterface
    interface MetadataReadSleeper {
        void sleep(long millis) throws IOException;
    }

    private static final class RetryableMetadataReadException extends IOException {

        private final Optional<Duration> retryAfter;

        private RetryableMetadataReadException(String message, Optional<Duration> retryAfter) {
            super(message);
            this.retryAfter = retryAfter;
        }

        private Optional<Duration> retryAfter() {
            return retryAfter;
        }
    }

    static List<String> getNewerVersionsFromLibraryIndex(String index, String startingVersion, String libraryName) {
        Pattern pattern = Pattern.compile("<version>(.*)</version>");
        Matcher matcher = pattern.matcher(index);
        List<String> allVersions = new ArrayList<>();

        while (matcher.find()) {
            allVersions.add(matcher.group(1));
        }

        int indexOfStartingVersion = allVersions.indexOf(startingVersion);
        if (indexOfStartingVersion < 0) {
            return new ArrayList<>();
        }

        allVersions = allVersions.subList(indexOfStartingVersion, allVersions.size());
        return new ArrayList<>(allVersions.subList(1, allVersions.size()));
    }

    static List<String> filterPreReleases(List<String> versions) {
        Set<String> releases = new HashSet<>();
        for (String v : versions) {
            Matcher m = TestedVersionUpdaterTask.VERSION_PATTERN.matcher(v);
            if (m.matches() && m.group(2) == null) {
                releases.add(m.group(1));
            }
        }

        List<String> result = new ArrayList<>();
        for (String v : versions) {
            Matcher m = TestedVersionUpdaterTask.VERSION_PATTERN.matcher(v);
            if (m.matches()) {
                String base = m.group(1);
                String preSuffix = m.groupCount() > 1 ? m.group(2) : null;
                if (preSuffix == null || !releases.contains(base)) {
                    result.add(v);
                }
            } else {
                result.add(v);
            }
        }
        return result;
    }

    static String getLatestLibraryVersion(String libraryModule) {
        try {
            List<String> testedVersions = getTestedVersions(libraryModule);
            if (testedVersions.isEmpty()) {
                throw new IllegalStateException("Cannot find any tested version for: " + libraryModule);
            }
            testedVersions.sort(Comparator.comparing(VersionNumber::parse));
            return testedVersions.get(testedVersions.size() - 1);
        } catch (RuntimeException e) {
            throw e;
        }
    }

    static List<String> getTestedVersions(String libraryModule) {
        try {
            String[] coordinates = libraryModule.split(":");
            String group = coordinates[0];
            String artifact = coordinates[1];

            File indexFile = new File("metadata/" + group + "/" + artifact + "/index.json");
            if (!indexFile.exists()) {
                return Collections.emptyList();
            }

            ObjectMapper objectMapper = new ObjectMapper()
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL);

            List<MetadataVersionsIndexEntry> entries = objectMapper.readValue(
                    indexFile, new TypeReference<List<MetadataVersionsIndexEntry>>() {});
            List<String> testedVersions = new ArrayList<>();
            for (MetadataVersionsIndexEntry entry : entries) {
                if (entry != null && entry.testedVersions() != null) {
                    testedVersions.addAll(entry.testedVersions());
                }
            }
            return testedVersions;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static List<String> getSkippedVersions(String libraryModule) {
        try {
            String[] coordinates = libraryModule.split(":");
            String group = coordinates[0];
            String artifact = coordinates[1];

            File coordinatesMetadataIndex = new File("metadata/" + group + "/" + artifact + "/index.json");
            if (!coordinatesMetadataIndex.exists()) {
                throw new RuntimeException("Missing index.json for " + libraryModule);
            }

            ObjectMapper objectMapper = new ObjectMapper()
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL);

            List<MetadataVersionsIndexEntry> entries = objectMapper.readValue(
                    coordinatesMetadataIndex, new TypeReference<List<MetadataVersionsIndexEntry>>() {});

            List<String> skipped = new ArrayList<>();
            for (MetadataVersionsIndexEntry entry : entries) {
                if (entry != null && entry.skippedVersions() != null) {
                    for (SkippedVersionEntry sve : entry.skippedVersions()) {
                        skipped.add(sve.version());
                    }
                }
            }
            return skipped;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
