/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.graalvm.internal.tck.model.MetadataVersionsIndexEntry;
import org.graalvm.internal.tck.stats.LibraryStatsModels;
import org.graalvm.internal.tck.stats.LibraryStatsSupport;

import org.graalvm.internal.tck.utils.CoordinateUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.util.internal.VersionNumber;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@SuppressWarnings("unused")
public abstract class TestedVersionUpdaterTask extends DefaultTask {
    private static final String VERSION_PLACEHOLDER = "$version$";
    private static final Duration URL_VERIFICATION_TIMEOUT = Duration.ofSeconds(20);
    private static final int MAX_VERIFICATION_DOWNLOAD_BYTES = 25 * 1024 * 1024;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(URL_VERIFICATION_TIMEOUT)
            .build();
    private static final List<String> SOURCE_FILE_EXTENSIONS = List.of(".java", ".kt", ".scala", ".groovy");
    private static final Pattern URL_VERSION_TOKEN_PATTERN = Pattern.compile(
            "(?i)(?<![A-Za-z0-9])\\d+(?:\\.\\d+)+(?:\\.Final|\\.RELEASE)?(?:[-.](?:alpha\\d*|beta\\d*|rc\\d*|cr\\d*|m\\d+|ea\\d*|b\\d+|\\d+|preview))?(?![A-Za-z0-9])"
    );

    /**
     * Identifies library versions, including optional pre-release, ".Final" and ".RELEASE" suffixes.
     * <p>
     * A version is considered a pre-release if it has a suffix (following the last '.' or '-') matching
     * one of these case-insensitive patterns:
     * <ul>
     *   <li>{@code alpha} followed by optional numbers (e.g., "alpha", "Alpha1", "alpha123")</li>
     *   <li>{@code beta} followed by optional numbers (e.g., "beta", "Beta2", "BETA45")</li>
     *   <li>{@code rc} followed by optional numbers (e.g., "rc", "RC1", "rc99")</li>
     *   <li>{@code cr} followed by optional numbers (e.g., "cr", "CR3", "cr10")</li>
     *   <li>{@code m} followed by REQUIRED numbers (e.g., "M1", "m23")</li>
     *   <li>{@code ea} followed by optional numbers (e.g., "ea", "ea2", "ea15")</li>
     *   <li>{@code b} followed by REQUIRED numbers (e.g., "b0244", "b5")</li>
     *   <li>{@code preview} followed by optional numbers (e.g., "preview", "preview1", "preview42")</li>
     *   <li>Numeric suffixes separated by '-' (e.g., "-1", "-123")</li>
     * </ul>
     * <p>
     * Versions ending with ".Final" or `.RELEASE` are treated as full releases of the base version.
     */
    public static final Pattern VERSION_PATTERN = Pattern.compile(
            "(?i)^(\\d+(?:\\.\\d+)*)(?:\\.Final|\\.RELEASE)?(?:[-.](alpha\\d*|beta\\d*|rc\\d*|cr\\d*|m\\d+|ea\\d*|b\\d+|\\d+|preview)(?:[-.].*)?)?$"
    );

    @Option(option = "coordinates", description = "GAV coordinates of the library")
    void setCoordinates(String c) {
        extractInformationFromCoordinates(c);
    }

    {
        // Prefer task option, fallback to -Pcoordinates
        String coordinates = (String) getProject().findProperty("coordinates");
        if (coordinates != null && !getIndexFile().isPresent()) {
            extractInformationFromCoordinates(coordinates);
        }
    }

    private void extractInformationFromCoordinates(String c) {
        String[] coordinatesParts = c.split(":");
        if (coordinatesParts.length != 3) {
            throw new IllegalArgumentException("Maven coordinates should have 3 parts");
        }
        String group = coordinatesParts[0];
        String artifact = coordinatesParts[1];
        String version = coordinatesParts[2];
        Coordinates coordinates = new Coordinates(group, artifact, version);

        getIndexFile().set(getProject().file(CoordinateUtils.replace("metadata/$group$/$artifact$/index.json", coordinates)));
        getNewVersion().set(version);
    }

    @Input
    @Option(option = "lastSupportedVersion", description = "Last version of the library that passed tests")
    protected abstract Property<@NotNull String> getLastSupportedVersion();

    @Input
    protected abstract Property<@NotNull String> getNewVersion();

    @OutputFiles
    protected abstract RegularFileProperty getIndexFile();

    @TaskAction
    void run() throws IllegalStateException, IOException {
        File coordinatesMetadataIndex = getIndexFile().get().getAsFile();
        String newVersion = getNewVersion().get();
        ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).setSerializationInclusion(JsonInclude.Include.NON_NULL);

        List<MetadataVersionsIndexEntry> entries = objectMapper.readValue(coordinatesMetadataIndex, new TypeReference<>() {});
        if (entries.stream().anyMatch(MetadataVersionsIndexEntry::isNotForNativeImage)) {
            throw new IllegalStateException("Cannot add tested versions to not-for-native-image artifact: " + coordinatesMetadataIndex);
        }
        for (int i = 0; i < entries.size(); i++) {
            MetadataVersionsIndexEntry entry = entries.get(i);

            if (entry.testedVersions() != null && entry.testedVersions().contains(getLastSupportedVersion().get())) {
                entry.testedVersions().add(newVersion);
                entry.testedVersions().sort(Comparator.comparing(VersionNumber::parse));

                entries.set(i, handlePreReleases(entry, newVersion, coordinatesMetadataIndex.toPath().getParent(), entries));
            }
        }

        DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter();
        prettyPrinter.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);

        // Ensure the JSON file ends with a trailing EOL
        String json = objectMapper.writer(prettyPrinter).writeValueAsString(entries);
        if (!json.endsWith("\n")) {
            json = json + System.lineSeparator();
        }
        Files.writeString(coordinatesMetadataIndex.toPath(), json, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Handles pre-release versions when adding a new version to a metadata entry.
     * <p>
     * Rules applied by this method:
     * <ul>
     *   <li>If the newly added version is a full release (no pre-release label, or ending with ".Final" or ".RELEASE"),
     *       all existing pre-releases of the same base version are removed from {@code testedVersions}.</li>
     *   <li>If the newly added version is itself a pre-release, no versions are removed.</li>
     *   <li>If the entry's {@code metadataVersion} is a pre-release of the same base version,
     *       it is updated to the new full release. The corresponding metadata and test directories are renamed accordingly.
     *       The {@code gradle.properties} file in the tests directory is updated to refer to the new version.</li>
     *   <li>Versioned artifact URL fields are rendered for the full release and verified before filesystem renames.</li>
     *   <li>Version parsing follows {@link #VERSION_PATTERN} and treats ".Final" and ".RELEASE" as a base version.</li>
     * </ul>
     */
    private MetadataVersionsIndexEntry handlePreReleases(MetadataVersionsIndexEntry entry, String newVersion, Path baseDir, List<MetadataVersionsIndexEntry> entries) throws IOException {
        Matcher versionMatcher = VERSION_PATTERN.matcher(newVersion);
        if (!versionMatcher.matches()) return entry; // skip invalid formats

        String baseVersion = versionMatcher.group(1);
        // Only remove old pre-releases if this is a full release
        if (versionMatcher.group(2) == null) {
            entry.testedVersions().removeIf(version -> {
                Matcher existingVersionMatcher = VERSION_PATTERN.matcher(version);
                return existingVersionMatcher.matches() && existingVersionMatcher.group(2) != null && baseVersion.equals(existingVersionMatcher.group(1));
            });

            // Update metadata version if it was a pre-release of the same base
            String oldMetadata = entry.metadataVersion();
            Matcher metaMatcher = VERSION_PATTERN.matcher(oldMetadata);
            if (metaMatcher.matches() && metaMatcher.group(2) != null && baseVersion.equals(metaMatcher.group(1))) {
                PromotedUrls promotedUrls = promoteArtifactUrls(entry, oldMetadata, newVersion);
                Path oldDir = baseDir.resolve(oldMetadata);
                Path newDir = baseDir.resolve(newVersion);
                if (Files.exists(oldDir)) Files.move(oldDir, newDir);

                updateTests(baseDir, entry, oldMetadata, newVersion);
                updateStats(baseDir, oldMetadata, newVersion);
                updateDependentTestVersions(oldMetadata, newVersion, entries);
                return new MetadataVersionsIndexEntry(
                        entry.latest(),
                        entry.override(),
                        entry.defaultFor(),
                        newVersion,
                        entry.testVersion(),
                        promotedUrls.sourceCodeUrl(),
                        entry.repositoryUrl(),
                        promotedUrls.testCodeUrl(),
                        promotedUrls.documentationUrl(),
                        entry.description(),
                        entry.language(),
                        entry.testedVersions(),
                        entry.skippedVersions(),
                        entry.allowedPackages(),
                        entry.requires(),
                        null,
                        null,
                        null
                );
            }
        }

        return entry;
    }

    private PromotedUrls promoteArtifactUrls(MetadataVersionsIndexEntry entry, String oldVersion, String newVersion) {
        return new PromotedUrls(
                promoteUrl("source-code-url", entry.sourceCodeUrl(), oldVersion, newVersion, UrlVerification.ARCHIVE_WITH_SOURCES),
                promoteUrl("test-code-url", entry.testCodeUrl(), oldVersion, newVersion, UrlVerification.TEST_CODE),
                promoteUrl("documentation-url", entry.documentationUrl(), oldVersion, newVersion, UrlVerification.RESOLVES)
        );
    }

    private String promoteUrl(String fieldName, String currentUrl, String oldVersion, String newVersion, UrlVerification verification) {
        if (!hasText(currentUrl) || "N/A".equals(currentUrl)) {
            return currentUrl;
        }

        String promotedUrl = renderVersionTemplate(currentUrl, oldVersion, newVersion);
        verifyPromotedUrl(fieldName, promotedUrl, oldVersion, newVersion, verification);
        return toStoredVersionTemplate(promotedUrl, newVersion);
    }

    private void verifyPromotedUrl(String fieldName, String promotedUrl, String oldVersion, String newVersion, UrlVerification verification) {
        if (!promotedUrl.contains(newVersion)) {
            throw staleUrlException(fieldName, promotedUrl, oldVersion, newVersion, "URL does not contain the promoted metadata version");
        }
        if (containsIgnoreCase(promotedUrl, oldVersion)) {
            throw staleUrlException(fieldName, promotedUrl, oldVersion, newVersion, "URL still contains the old pre-release version");
        }

        try {
            if (verification == UrlVerification.ARCHIVE_WITH_SOURCES) {
                verifySourceUrl(promotedUrl);
            } else if (verification == UrlVerification.TEST_CODE && isArchiveUrl(promotedUrl)) {
                verifyArchiveContainsSourceFiles(promotedUrl);
            } else {
                verifyUrlResolves(promotedUrl);
            }
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
        Matcher newVersionMatcher = VERSION_PATTERN.matcher(newVersion);
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

            Matcher candidateMatcher = VERSION_PATTERN.matcher(urlVersion);
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

    private void verifySourceUrl(String url) throws IOException, InterruptedException {
        if (isArchiveUrl(url)) {
            verifyArchiveContainsSourceFiles(url);
        } else {
            verifyUrlResolves(url);
        }
    }

    private GradleException staleUrlException(String fieldName, String promotedUrl, String oldVersion, String newVersion, String reason) {
        return new GradleException(
                "Cannot promote " + fieldName + " from " + oldVersion + " to " + newVersion + ". " +
                        "Candidate URL failed verification: " + promotedUrl + ". Reason: " + reason
        );
    }

    private void verifyUrlResolves(String url) throws IOException, InterruptedException {
        HttpResponse<Void> response = sendRequest(url, "HEAD", HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() == 405 || response.statusCode() == 403) {
            response = sendRequest(url, "GET", HttpResponse.BodyHandlers.discarding());
        }
        verifySuccessStatus(url, response.statusCode());
    }

    private void verifyArchiveContainsSourceFiles(String url) throws IOException, InterruptedException {
        HttpResponse<byte[]> response = sendRequest(url, "GET", HttpResponse.BodyHandlers.ofByteArray());
        verifySuccessStatus(url, response.statusCode());
        byte[] archiveBytes = response.body();
        if (archiveBytes.length > MAX_VERIFICATION_DOWNLOAD_BYTES) {
            throw new IOException("archive is too large to verify (" + archiveBytes.length + " bytes)");
        }

        boolean hasSourceFiles = false;
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(archiveBytes))) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                if (!zipEntry.isDirectory() && isSourceFile(zipEntry.getName())) {
                    hasSourceFiles = true;
                    break;
                }
            }
        }

        if (!hasSourceFiles) {
            throw new IOException("archive does not contain .java, .kt, .scala, or .groovy source files");
        }
    }

    private <T> HttpResponse<T> sendRequest(String url, String method, HttpResponse.BodyHandler<T> bodyHandler) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(URL_VERIFICATION_TIMEOUT)
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
        return HTTP_CLIENT.send(request, bodyHandler);
    }

    private void verifySuccessStatus(String url, int statusCode) throws IOException {
        if (statusCode < 200 || statusCode >= 300) {
            throw new IOException("URL did not resolve successfully: HTTP " + statusCode + " for " + url);
        }
    }

    private static boolean isArchiveUrl(String url) {
        String normalizedUrl = url.toLowerCase(Locale.ROOT);
        return normalizedUrl.endsWith(".jar") || normalizedUrl.endsWith(".zip");
    }

    private static boolean isSourceFile(String path) {
        return SOURCE_FILE_EXTENSIONS.stream().anyMatch(path::endsWith);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Updates the tests directory when a pre-release version is replaced by its corresponding
     * full release.
     * <p>
     * This method performs two operations:
     * <ol>
     *   <li>Renames the test directory from {@code oldVersion} to {@code newVersion} under {@code tests/src/<group>/<artifact>/}.</li>
     *   <li>Updates the {@code gradle.properties} file inside the renamed directory:
     *     <ul>
     *       <li>{@code library.version} is set to the new version,</li>
     *       <li>{@code metadata.dir} is updated to point to the metadata directory for the new version.</li>
     *     </ul>
     *   </li>
     * </ol>
     */
    private void updateTests(Path metadataBaseDir, MetadataVersionsIndexEntry entry, String oldVersion, String newVersion) throws IOException {
        // metadataBaseDir points to metadata/<group>/<artifact>
        String artifact = metadataBaseDir.getFileName().toString();
        String group = metadataBaseDir.getParent().getFileName().toString();
        Path testsRoot = metadataBaseDir.getParent().getParent().getParent()
                .resolve("tests/src").resolve(group).resolve(artifact);

        Path oldTestDir = testsRoot.resolve(oldVersion);
        Path newTestDir = testsRoot.resolve(newVersion);

        if (Files.exists(oldTestDir)) {
            Files.move(oldTestDir, newTestDir);
            updateGradleProperties(newTestDir, group, artifact, entry, newVersion);
        }
    }

    /**
     * Updates {@code gradle.properties} inside a given test directory to reflect the new version.
     */
    private void updateGradleProperties(Path testDir, String group, String artifact, MetadataVersionsIndexEntry entry, String newVersion) throws IOException {
        Path gradleProps = testDir.resolve("gradle.properties");
        if (!Files.exists(gradleProps)) return;

        List<String> lines = Files.readAllLines(gradleProps);
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("library.version")) lines.set(i, "library.version = " + newVersion);
            else if (lines.get(i).startsWith("library.coordinates")) lines.set(i, "library.coordinates = " + group + ":" + artifact + ":" + newVersion);
            else if (lines.get(i).startsWith("metadata.dir"))
                lines.set(i, "metadata.dir = " + group + "/" + artifact + "/" + newVersion + "/");
        }
        Files.write(gradleProps, lines);
    }

    /**
     * Updates exploded stats when a pre-release metadata version is replaced by its full release.
     */
    private void updateStats(Path metadataBaseDir, String oldVersion, String newVersion) throws IOException {
        String artifact = metadataBaseDir.getFileName().toString();
        String group = metadataBaseDir.getParent().getFileName().toString();
        Path repositoryRoot = metadataBaseDir.getParent().getParent().getParent();
        Path statsRoot = repositoryRoot.resolve("stats");
        Path oldStatsDir = statsRoot.resolve(group).resolve(artifact).resolve(oldVersion);
        Path newStatsDir = statsRoot.resolve(group).resolve(artifact).resolve(newVersion);

        Path oldStatsFile = LibraryStatsSupport.repositoryStatsFile(statsRoot, group, artifact, oldVersion);
        if (Files.exists(oldStatsFile)) {
            LibraryStatsModels.MetadataVersionStats metadataVersionStats = LibraryStatsSupport.loadMetadataVersionStats(oldStatsFile);
            LibraryStatsModels.MetadataVersionStats updatedStats = new LibraryStatsModels.MetadataVersionStats(
                    metadataVersionStats.versions().stream()
                            .map(versionStats -> oldVersion.equals(versionStats.version())
                                    ? new LibraryStatsModels.VersionStats(newVersion, versionStats.dynamicAccess(), versionStats.libraryCoverage())
                                    : versionStats)
                            .toList()
            );

            Path newStatsFile = LibraryStatsSupport.repositoryStatsFile(statsRoot, group, artifact, newVersion);
            LibraryStatsSupport.writeMetadataVersionStats(newStatsFile, updatedStats);
            if (!oldStatsFile.equals(newStatsFile)) {
                Files.deleteIfExists(oldStatsFile);
            }
        }
        updateExecutionMetrics(oldStatsDir, newStatsDir, group, artifact, oldVersion, newVersion);
        updateExecutionMetricReferences(statsRoot.resolve(group).resolve(artifact), group, artifact, oldVersion, newVersion);

        if (!oldStatsDir.equals(newStatsDir)) {
            try {
                Files.deleteIfExists(oldStatsDir);
            } catch (DirectoryNotEmptyException ignored) {
                // Keep the old directory when other stats artifacts still exist in it.
            }
        }
    }

    /**
     * Updates committed Forge execution metrics when the metadata-version directory is promoted.
     */
    private void updateExecutionMetrics(Path oldStatsDir, Path newStatsDir, String group, String artifact, String oldVersion, String newVersion) throws IOException {
        Path oldMetricsFile = oldStatsDir.resolve("execution-metrics.json");
        if (!Files.exists(oldMetricsFile)) return;

        ObjectMapper objectMapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        ObjectNode executionMetrics = (ObjectNode) objectMapper.readTree(oldMetricsFile.toFile());
        executionMetrics.properties().forEach(entry -> updateRunMetrics(entry.getValue(), group, artifact, oldVersion, newVersion));

        Files.createDirectories(newStatsDir);
        Path newMetricsFile = newStatsDir.resolve("execution-metrics.json");
        writeJsonWithTrailingNewline(objectMapper, newMetricsFile, executionMetrics);
        if (!oldMetricsFile.equals(newMetricsFile)) {
            Files.deleteIfExists(oldMetricsFile);
        }
    }

    /**
     * Updates committed Forge execution metrics in other versions for the same library.
     */
    private void updateExecutionMetricReferences(Path libraryStatsRoot, String group, String artifact, String oldVersion, String newVersion) throws IOException {
        if (!Files.exists(libraryStatsRoot)) return;

        ObjectMapper objectMapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);

        try (Stream<Path> paths = Files.find(
                libraryStatsRoot,
                2,
                (path, attributes) -> attributes.isRegularFile() && "execution-metrics.json".equals(path.getFileName().toString())
        )) {
            for (Path metricsFile : paths.toList()) {
                ObjectNode executionMetrics = (ObjectNode) objectMapper.readTree(metricsFile.toFile());
                boolean changed = false;
                for (Map.Entry<String, JsonNode> entry : executionMetrics.properties()) {
                    changed |= updateRunMetrics(entry.getValue(), group, artifact, oldVersion, newVersion);
                }
                if (changed) {
                    writeJsonWithTrailingNewline(objectMapper, metricsFile, executionMetrics);
                }
            }
        }
    }

    private boolean updateRunMetrics(JsonNode runMetrics, String group, String artifact, String oldVersion, String newVersion) {
        if (!(runMetrics instanceof ObjectNode objectNode)) return false;

        boolean changed = false;
        changed |= updateCoordinateField(objectNode, "library", group, artifact, oldVersion, newVersion);
        changed |= updateCoordinateField(objectNode, "previous_library", group, artifact, oldVersion, newVersion);
        changed |= updateStatsVersion(objectNode.get("stats"), oldVersion, newVersion);
        changed |= updateStatsVersion(objectNode.get("previous_library_stats"), oldVersion, newVersion);
        changed |= updateArtifactPaths(objectNode.get("artifacts"), oldVersion, newVersion);
        return changed;
    }

    private boolean updateCoordinateField(ObjectNode runMetrics, String fieldName, String group, String artifact, String oldVersion, String newVersion) {
        JsonNode coordinateNode = runMetrics.get(fieldName);
        if (coordinateNode == null || !coordinateNode.isTextual()) return false;

        String oldCoordinate = group + ":" + artifact + ":" + oldVersion;
        if (oldCoordinate.equals(coordinateNode.asText())) {
            runMetrics.put(fieldName, group + ":" + artifact + ":" + newVersion);
            return true;
        }
        return false;
    }

    private boolean updateStatsVersion(JsonNode stats, String oldVersion, String newVersion) {
        if (!(stats instanceof ObjectNode statsObject)) return false;

        JsonNode versionNode = statsObject.get("version");
        if (versionNode != null && versionNode.isTextual() && oldVersion.equals(versionNode.asText())) {
            statsObject.put("version", newVersion);
            return true;
        }
        return false;
    }

    private boolean updateArtifactPaths(JsonNode artifacts, String oldVersion, String newVersion) {
        if (!(artifacts instanceof ObjectNode artifactsObject)) return false;

        boolean changed = false;
        for (Map.Entry<String, JsonNode> entry : artifactsObject.properties()) {
            JsonNode value = entry.getValue();
            if (value != null && value.isTextual()) {
                String updatedValue = value.asText().replace("/" + oldVersion + "/", "/" + newVersion + "/");
                if (!updatedValue.equals(value.asText())) {
                    artifactsObject.put(entry.getKey(), updatedValue);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private void writeJsonWithTrailingNewline(ObjectMapper objectMapper, Path path, JsonNode jsonNode) throws IOException {
        DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter();
        prettyPrinter.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);

        String json = objectMapper.writer(prettyPrinter).writeValueAsString(jsonNode);
        if (!json.endsWith("\n")) {
            json = json + System.lineSeparator();
        }
        Files.writeString(path, json, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Checks all entries in the index file to see if any are using the old metadata version
     * (which was a pre-release) as their 'test-version' override, and updates it to the new
     * full-release version if a match is found.
     */
    private void updateDependentTestVersions(String oldTestVersion, String newTestVersion, List<MetadataVersionsIndexEntry> entries) {
        for (int i = 0; i < entries.size(); i++) {
            MetadataVersionsIndexEntry entry = entries.get(i);
            if (entry.isNotForNativeImage()) {
                continue;
            }

            // Check if the entry explicitly points to the old directory via 'test-version'
            if (oldTestVersion.equals(entry.testVersion())) {
                // Create a new entry with the updated test-version
                MetadataVersionsIndexEntry updatedEntry = new MetadataVersionsIndexEntry(
                        entry.latest(),
                        entry.override(),
                        entry.defaultFor(),
                        entry.metadataVersion(),
                        newTestVersion,
                        entry.sourceCodeUrl(),
                        entry.repositoryUrl(),
                        entry.testCodeUrl(),
                        entry.documentationUrl(),
                        entry.description(),
                        entry.language(),
                        entry.testedVersions(),
                        entry.skippedVersions(),
                        entry.allowedPackages(),
                        entry.requires(),
                        null,
                        null,
                        null
                );
                entries.set(i, updatedEntry);
            }
        }
    }

    private enum UrlVerification {
        ARCHIVE_WITH_SOURCES,
        TEST_CODE,
        RESOLVES
    }

    private record PromotedUrls(
            String sourceCodeUrl,
            String testCodeUrl,
            String documentationUrl
    ) {
    }
}
