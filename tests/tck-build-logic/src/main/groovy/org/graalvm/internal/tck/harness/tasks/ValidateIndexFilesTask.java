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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Single task that:
 * 1) Resolves a set of index.json files based on selected coordinates (supports fractional batching k/n)
 * 2) Always includes the root <code>metadata/index.json</code> for validation
 * 3) Maps each file to its corresponding JSON Schema (root or library level)
 * 4) Validates the JSON well-formedness and schema compliance
 * 5) Collects and reports all validation failures at the end of execution
 * <p>
 * Coordinates can be provided via:
 * - -Pcoordinates=<filter> (preferred, supports space-separated lists for CI)
 * - --coordinates=<filter>
 * The filter can be <code>group:artifact[:version]</code>, a fractional batch <code>k/n</code> (e.g., 1/16), or 'all'.
 */
public abstract class ValidateIndexFilesTask extends CoordinatesAwareTask {

    private static final Pattern METADATA_PATTERN = Pattern.compile("metadata/[^/]+/[^/]+/index\\.json");

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

        // 1. Always include the root index
        targetFiles.add("metadata/index.json");

        // 2. Resolve coordinates
        List<String> allResolved = new ArrayList<>();
        List<String> override = getCoordinatesOverride().getOrElse(Collections.emptyList());

        if (!override.isEmpty()) {
            allResolved.addAll(override);
        } else {
            String filter = effectiveCoordinateFilter();
            // Split by whitespace to support lists passed from GitHub Actions/CLI
            for (String singleFilter : filter.split("\\s+")) {
                if (!singleFilter.isEmpty()) {
                    allResolved.addAll(computeMatchingCoordinates(singleFilter));
                }
            }
        }

        // 3. Map resolved coordinates back to physical file paths
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

    private void executeValidation(Set<String> targetFiles) {
        ObjectMapper mapper = new ObjectMapper();
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        Map<String, JsonSchema> schemaCache = new HashMap<>();
        List<String> failures = new ArrayList<>();

        for (String filePath : targetFiles) {
            File jsonFile = getProject().file(filePath.replace('\\', '/'));

            if (!jsonFile.exists()) {
                if ("metadata/index.json".equals(filePath)) {
                    failures.add("❌ Root index missing: " + filePath);
                } else {
                    getLogger().warn("⚠️ File not found: " + filePath);
                }
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
     * Ensures "tested-versions" are mapped to the most appropriate metadata entry.
     * <p>
     * Rule: A version in {@code tested-versions} must be strictly less than the
     * next higher {@code metadata-version} available in the file.
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
        if ("metadata/index.json".equals(filePath)) {
            return "metadata/schemas/metadata-root-index-schema-v1.0.0.json";
        }
        if (METADATA_PATTERN.matcher(filePath).matches()) {
            return "metadata/schemas/metadata-library-index-schema-v1.0.0.json";
        }
        return "";
    }
}
