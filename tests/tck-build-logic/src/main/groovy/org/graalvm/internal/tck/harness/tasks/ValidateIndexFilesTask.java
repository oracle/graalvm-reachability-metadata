/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.gradle.api.GradleException;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.jetbrains.annotations.NotNull;
import org.gradle.util.internal.VersionNumber;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Single task that:
 * 1) Resolves a set of index.json files based on selected coordinates (supports fractional batching k/n)
 * 2) Maps each file to its corresponding JSON Schema
 * 3) Validates the JSON well-formedness and schema compliance
 * 4) Collects and reports all validation failures at the end of execution
 * <p>
 * Coordinates can be provided via:
 * - -Pcoordinates=<filter> (preferred, supports space-separated lists for CI)
 * - --coordinates=<filter>
 * The filter can be <code>group:artifact[:version]</code>, a fractional batch <code>k/n</code> (e.g., 1/16), or 'all'.
 */
public abstract class ValidateIndexFilesTask extends CoordinatesAwareTask {

    private static final Pattern METADATA_PATTERN = Pattern.compile("metadata/[^/]+/[^/]+/index\\.json");
    private static final Pattern METADATA_COORDINATE_PATTERN = Pattern.compile("metadata/([^/]+)/([^/]+)/index\\.json");
    private static final String VERSION_TOKEN = "$version$";
    private static final List<String> URL_TEMPLATE_FIELDS = List.of(
            "source-code-url",
            "test-code-url",
            "documentation-url"
    );
    private static final Set<String> MAVEN_CENTRAL_HOSTS = Set.of(
            "repo1.maven.org",
            "repo.maven.apache.org"
    );

    @Input
    @Optional
    public abstract Property<@NotNull String> getCoordinates();

    @Option(option = "coordinates", description = "Coordinate filter (group[:artifact[:version]] or k/n fractional batch)")
    public void setCoordinatesOption(String value) {
        getCoordinates().set(value);
    }

    /**
     * Determines the effective filter string by checking the CLI option first,
     * then fallback to the project property.
     */
    protected String effectiveCoordinateFilter() {
        String opt = getCoordinates().getOrNull();
        if (opt != null) {
            return opt;
        }
        Object prop = getProject().findProperty("coordinates");
        return prop == null ? "" : prop.toString();
    }

    @TaskAction
    public void validate() {
        Set<String> targetFiles = new LinkedHashSet<>();

        // 1. Resolve coordinates
        List<String> allResolved = new ArrayList<>();
        List<String> override = getCoordinatesOverride().getOrElse(Collections.emptyList());

        if (!override.isEmpty()) {
            allResolved.addAll(override);
        } else {
            String filter = effectiveCoordinateFilter();
            // Split by whitespace to support lists passed from GitHub Actions/CLI
            for (String singleFilter : filter.split("\\s+")) {
                if (!singleFilter.isEmpty()) {
                    String directIndexPath = directMetadataIndexPath(singleFilter);
                    if (directIndexPath != null) {
                        targetFiles.add(directIndexPath);
                    }
                    allResolved.addAll(computeMatchingCoordinates(singleFilter));
                }
            }
        }

        // 2. Map resolved coordinates back to physical file paths
        allResolved.stream()
                .filter(coord -> !coord.startsWith("samples:"))
                .distinct()
                .forEach(coord -> {
                    String[] parts = coord.split(":");
                    if (parts.length >= 2) {
                        targetFiles.add(String.format("metadata/%s/%s/index.json", parts[0], parts[1]));
                    }
                });

        executeValidation(targetFiles);
    }

    private String directMetadataIndexPath(String coordinateFilter) {
        String[] parts = coordinateFilter.split(":");
        if (parts.length < 2 || parts.length > 3) {
            return null;
        }
        String filePath = String.format("metadata/%s/%s/index.json", parts[0], parts[1]);
        return getProject().file(filePath).exists() ? filePath : null;
    }

    private void executeValidation(Set<String> targetFiles) {
        ObjectMapper mapper = new ObjectMapper();
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        Map<String, JsonSchema> schemaCache = new HashMap<>();
        List<String> failures = new ArrayList<>();

        for (String filePath : targetFiles) {
            File jsonFile = getProject().file(filePath.replace('\\', '/'));

            if (!jsonFile.exists()) {
                getLogger().warn("⚠️ File not found: " + filePath);
                continue;
            }

            String schemaPath = mapToSchemaPath(filePath);
            if (schemaPath.isEmpty()) continue;

            try {
                JsonSchema schema = schemaCache.computeIfAbsent(schemaPath, path ->
                        factory.getSchema(getProject().file(path).toURI())
                );

                JsonNode json = mapper.readTree(jsonFile);
                int beforeFailures = failures.size();

                // Schema validation
                Set<ValidationMessage> errors = schema.validate(json);
                if (!errors.isEmpty()) {
                    for (ValidationMessage err : errors) {
                        failures.add("❌ " + filePath + ": " + err.getMessage());
                    }
                }

                // Additional semantic validations for library metadata index files
                if (METADATA_PATTERN.matcher(filePath).matches()) {
                    checkLibraryIndexTestedVersions(json, filePath, failures);
                    checkLibraryIndexUrlTemplates(json, filePath, failures);
                }

                // Print success only if no new failures were added by schema or semantic checks
                if (failures.size() == beforeFailures) {
                    getLogger().lifecycle("✅ " + filePath + ": Valid");
                }
            } catch (Exception e) {
                failures.add("💥 " + filePath + ": Parse Error (" + e.getMessage() + ")");
            }
        }

        if (!failures.isEmpty()) {
            failures.forEach(f -> getLogger().error(f));
            throw new GradleException("Validation failed for " + failures.size() + " file(s).");
        }
    }

    /**
     * Checks Maven Central URL templates against every tested-version alias in the entry.
     * <p>
     * This is intentionally a structural check instead of an HTTP probe so the global
     * index validator remains deterministic. It catches templates such as
     * {@code $version$+1} on entries that also contain aliases like {@code 13} or
     * {@code 13-ea+1}, where rendering creates untracked Maven artifact versions.
     */
    static void checkLibraryIndexUrlTemplates(JsonNode json, String filePath, List<String> failures) {
        if (json == null || !json.isArray()) {
            return;
        }

        Matcher coordinateMatcher = METADATA_COORDINATE_PATTERN.matcher(filePath);
        if (!coordinateMatcher.matches()) {
            return;
        }

        String groupPath = coordinateMatcher.group(1).replace('.', '/');
        String artifact = coordinateMatcher.group(2);

        for (JsonNode entry : json) {
            String metadataVersion = entry.path("metadata-version").isTextual()
                    ? entry.path("metadata-version").asText()
                    : "<unknown>";
            List<String> testedVersions = testedVersions(entry);
            if (testedVersions.size() <= 1) {
                continue;
            }

            Set<String> testedVersionSet = new LinkedHashSet<>(testedVersions);
            for (String fieldName : URL_TEMPLATE_FIELDS) {
                JsonNode urlNode = entry.get(fieldName);
                if (urlNode == null || !urlNode.isTextual()) {
                    continue;
                }

                String template = urlNode.asText();
                if (!template.contains(VERSION_TOKEN)) {
                    continue;
                }

                for (String testedVersion : testedVersions) {
                    String renderedUrl = template.replace(VERSION_TOKEN, testedVersion);
                    String renderedMavenVersion = mavenCentralArtifactVersion(renderedUrl, groupPath, artifact);
                    if (renderedMavenVersion == null || testedVersion.equals(renderedMavenVersion)) {
                        continue;
                    }
                    if (!testedVersionSet.contains(renderedMavenVersion)) {
                        failures.add("❌ " + filePath + ": " + fieldName
                                + " template under metadata-version " + metadataVersion
                                + " renders tested-version " + testedVersion
                                + " to Maven artifact version " + renderedMavenVersion
                                + ", which is not listed in tested-versions. Split the entry or use an alias-safe URL.");
                    }
                }
            }
        }
    }

    private static List<String> testedVersions(JsonNode entry) {
        JsonNode testedVersionsNode = entry.get("tested-versions");
        if (testedVersionsNode == null || !testedVersionsNode.isArray()) {
            return Collections.emptyList();
        }

        List<String> testedVersions = new ArrayList<>();
        for (JsonNode testedVersionNode : testedVersionsNode) {
            if (testedVersionNode != null && testedVersionNode.isTextual()) {
                testedVersions.add(testedVersionNode.asText());
            }
        }
        return testedVersions;
    }

    private static String mavenCentralArtifactVersion(String renderedUrl, String groupPath, String artifact) {
        URI uri;
        try {
            uri = URI.create(renderedUrl);
        } catch (IllegalArgumentException e) {
            return null;
        }

        String host = uri.getHost();
        if (host == null || !MAVEN_CENTRAL_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
            return null;
        }

        String expectedPrefix = "/maven2/" + groupPath + "/" + artifact + "/";
        String path = uri.getPath();
        if (path == null || !path.startsWith(expectedPrefix)) {
            return null;
        }

        String remainder = path.substring(expectedPrefix.length());
        int separator = remainder.indexOf('/');
        if (separator <= 0 || separator == remainder.length() - 1) {
            return null;
        }

        String artifactVersion = remainder.substring(0, separator);
        String fileName = remainder.substring(separator + 1);
        if (!fileName.startsWith(artifact + "-" + artifactVersion + "-")) {
            return null;
        }
        return artifactVersion;
    }

    /**
     * Ensures "tested-versions" are mapped to the most appropriate metadata entry.
     * <p>
     * Rule: A version in {@code tested-versions} must be strictly less than the
     * next higher {@code metadata-version} available in the file, unless the
     * entry is explicitly marked as {@code latest}.
     * <p>
     * This prevents "stray" versions from being associated with obsolete metadata
     * when a more recent metadata entry exists.
     *
     * @param json     The JSON array of index entries.
     * @param filePath Path for error reporting.
     * @param failures Accumulator for validation errors.
     */
    private static void checkLibraryIndexTestedVersions(JsonNode json, String filePath, List<String> failures) {
        if (json == null || !json.isArray()) {
            return;
        }

        // Collect unique metadata-version strings
        java.util.Set<String> metaStrings = new java.util.LinkedHashSet<>();
        for (JsonNode entry : json) {
            JsonNode mv = entry.get("metadata-version");
            if (mv != null && mv.isTextual()) {
                metaStrings.add(mv.asText());
            }
        }
        if (metaStrings.isEmpty()) {
            return;
        }

        // Parse and sort metadata versions
        List<VersionNumber> metasSorted = new ArrayList<>();
        for (String s : metaStrings) {
            try {
                metasSorted.add(VersionNumber.parse(s));
            } catch (Exception ignore) {
                // Ignore unparsable versions; schema should handle invalid shapes
            }
        }
        if (metasSorted.isEmpty()) {
            return;
        }
        metasSorted.sort(java.util.Comparator.naturalOrder());

        // For each entry, enforce: tested-version < next(metadata-version), if next exists
        for (JsonNode entry : json) {
            if (entry.path("latest").asBoolean(false)) {
                continue;
            }

            String underMetaStr = entry.path("metadata-version").isTextual() ? entry.get("metadata-version").asText() : null;
            if (underMetaStr == null) continue;

            VersionNumber underMeta;
            try {
                underMeta = VersionNumber.parse(underMetaStr);
            } catch (Exception ignore) {
                continue;
            }

            // Locate index of current metadata-version in the sorted list
            int idx = -1;
            for (int i = 0; i < metasSorted.size(); i++) {
                if (metasSorted.get(i).compareTo(underMeta) == 0) {
                    idx = i;
                    break;
                }
            }
            if (idx == -1) continue;

            VersionNumber nextMeta = (idx < metasSorted.size() - 1) ? metasSorted.get(idx + 1) : null;

            JsonNode tvs = entry.get("tested-versions");
            if (tvs != null && tvs.isArray() && nextMeta != null) {
                for (JsonNode tvNode : tvs) {
                    if (tvNode != null && tvNode.isTextual()) {
                        try {
                            VersionNumber tv = VersionNumber.parse(tvNode.asText());
                            // Must be strictly less than the next metadata-version
                            if (tv.compareTo(nextMeta) >= 0) {
                                failures.add("❌ " + filePath + ": tested-versions contains version " + tv
                                        + " not less than next metadata-version " + nextMeta
                                        + " (under metadata-version " + underMetaStr + ")");
                            }
                        } catch (Exception ignore) {
                            // ignore unparsable tested versions; schema should catch most cases
                        }
                    }
                }
            }
        }
    }

    public static String mapToSchemaPath(String filePath) {
        if (METADATA_PATTERN.matcher(filePath).matches()) {
            return "metadata/schemas/metadata-library-index-schema-v2.0.1.json";
        }
        return "";
    }
}
