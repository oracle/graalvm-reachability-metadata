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
 * 3) Maps each file to its corresponding JSON Schema (root, library, or test project level)
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
    private static final Pattern TEST_PATTERN = Pattern.compile("tests/src/[^/]+/[^/]+/[^/]+/index\\.json");

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
                        if (parts.length >= 3) {
                            targetFiles.add(String.format("tests/src/%s/%s/%s/index.json", parts[0], parts[1], parts[2]));
                        }
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
                } else if (!filePath.startsWith("tests/")) {
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
                Set<ValidationMessage> errors = schema.validate(json);

                if (errors.isEmpty()) {
                    getLogger().lifecycle("✅ " + filePath + ": Valid");
                } else {
                    for (ValidationMessage err : errors) {
                        failures.add("❌ " + filePath + ": " + err.getMessage());
                    }
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

    public static String mapToSchemaPath(String filePath) {
        if ("metadata/index.json".equals(filePath)) {
            return "schemas/metadata-root-index-schema-v1.0.0.json";
        }
        if (METADATA_PATTERN.matcher(filePath).matches()) {
            return "schemas/metadata-library-index-schema-v1.0.0.json";
        }
        if (TEST_PATTERN.matcher(filePath).matches()) {
            return "schemas/tests-project-index-schema-v1.0.0.json";
        }
        return "";
    }
}
