/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.ValidationMessage
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

/**
 * Validates JSON index files against JSON Schemas.
 */
abstract class ValidateIndexFilesTask extends CoordinatesAwareTask {

    @TaskAction
    void validate() {
        Set<String> targetFiles = new LinkedHashSet<>()

        // 1. Always include the root index
        targetFiles.add("metadata/index.json")

        // 2. Resolve multiple coordinates from the filter
        List<String> allResolved = []

        // Handle coordinatesOverride (from other tasks)
        List<String> override = getCoordinatesOverride().getOrElse([])
        if (!override.isEmpty()) {
            allResolved.addAll(override)
        } else {
            // Handle space-separated -Pcoordinates from command line/CI
            String filter = project.findProperty("coordinates") ?: ""
            filter.split(' ').findAll { !it.isEmpty() }.each { singleFilter ->
                allResolved.addAll(computeMatchingCoordinates(singleFilter))
            }
        }

        // 3. Map resolved coordinates to file paths
        allResolved.findAll { !it.startsWith("samples:") }.unique().each { coord ->
            String[] parts = coord.split(':')
            if (parts.length >= 2) {
                targetFiles.add("metadata/${parts[0]}/${parts[1]}/index.json")
                if (parts.length >= 3) {
                    targetFiles.add("tests/src/${parts[0]}/${parts[1]}/${parts[2]}/index.json")
                }
            }
        }

        // ... Rest of the validation logic (Mapper, Schema Cache, Loop) ...
        executeValidation(targetFiles)
    }

    private void executeValidation(Set<String> targetFiles) {
        ObjectMapper mapper = new ObjectMapper()
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)
        Map<String, JsonSchema> schemaCache = [:]
        List<String> failures = []

        for (String filePath : targetFiles) {
            File jsonFile = getProject().file(filePath)

            if (!jsonFile.exists()) {
                if (filePath == "metadata/index.json") {
                    failures.add("❌ Root index missing: ${filePath}")
                } else if (!filePath.startsWith("tests/")) {
                    logger.warn("⚠️ File not found: ${filePath}")
                }
                continue
            }

            String schemaPath = mapToSchemaPath(filePath)
            if (schemaPath.isEmpty()) continue

            try {
                JsonSchema schema = schemaCache.computeIfAbsent(schemaPath) { path ->
                    factory.getSchema(getProject().file(path).toURI())
                }

                JsonNode json = mapper.readTree(jsonFile)
                Set<ValidationMessage> errors = schema.validate(json)

                if (errors.isEmpty()) {
                    logger.lifecycle("✅ ${filePath}: Valid")
                } else {
                    errors.each { failures.add("❌ ${filePath}: ${it.message}") }
                }
            } catch (Exception e) {
                failures.add("💥 ${filePath}: Parse Error (${e.message})")
            }
        }

        if (!failures.isEmpty()) {
            failures.each { logger.error(it) }
            throw new GradleException("Validation failed for ${failures.size()} file(s).")
        }
    }

    static String mapToSchemaPath(String filePath) {
        if (filePath == "metadata/index.json") return "schemas/metadata-root-index-schema-v1.0.0.json"
        if (filePath ==~ /metadata\/[^\/]+\/[^\/]+\/index\.json/) return "schemas/metadata-library-index-schema-v1.0.0.json"
        if (filePath ==~ /tests\/src\/[^\/]+\/[^\/]+\/[^\/]+\/index\.json/) return "schemas/tests-project-index-schema-v1.0.0.json"
        return ""
    }
}
