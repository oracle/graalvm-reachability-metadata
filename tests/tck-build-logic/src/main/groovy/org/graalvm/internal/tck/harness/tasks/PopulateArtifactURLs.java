/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.graalvm.internal.tck.harness.TckExtension;
import org.graalvm.internal.tck.model.MetadataVersionsIndexEntry;
import org.graalvm.internal.tck.utils.CoordinateUtils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves strict coordinates and asks an external coding agent to fill
 * source-code, repository, test-code, and documentation URLs, a short library description,
 * and optional language-specific metadata in metadata index files.
 */
@SuppressWarnings("unused")
public class PopulateArtifactURLs extends DefaultTask {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String PROMPT_PLACEHOLDER = "{prompt}";
    private static final Pattern COMMAND_TOKEN_PATTERN = Pattern.compile("\"([^\"]*)\"|'([^']*)'|(\\S+)");
    private static final List<String> OPENCODE_RUN_COMMAND = List.of("opencode", "run");

    private String coordinates;
    private String agentCommand;
    private Integer limit;
    private boolean overwriteExisting;
    private boolean verifyArtifactSources;

    @Option(option = "coordinates", description = "Coordinate filter (group[:artifact[:version]] or k/n fractional batch).")
    public void setCoordinatesOption(String coordinates) {
        this.coordinates = coordinates;
    }

    @Input
    @Optional
    public String getCoordinates() {
        return coordinates;
    }

    @Option(
            option = "agent-command",
            description = "Coding agent command to execute. The prompt is appended as the last argument unless '{prompt}' is used. Use 'opencode run' to print prompts without invoking an external process."
    )
    public void setAgentCommandOption(String agentCommand) {
        this.agentCommand = agentCommand;
    }

    @Input
    @Optional
    public String getAgentCommand() {
        return agentCommand;
    }

    @Option(option = "limit", description = "Maximum number of coordinates to process.")
    public void setLimitOption(String limit) {
        try {
            this.limit = Integer.parseInt(limit);
        } catch (NumberFormatException e) {
            throw new GradleException("Invalid --limit value '" + limit + "'. Must be a positive integer.", e);
        }
    }

    @Input
    @Optional
    public Integer getLimit() {
        return limit;
    }

    @Option(option = "overwrite-existing", description = "Overwrite source-code-url, repository-url, test-code-url, documentation-url, description, and language when those fields already contain values.")
    public void setOverwriteExistingOption(boolean overwriteExisting) {
        this.overwriteExisting = overwriteExisting;
    }

    @Input
    public boolean isOverwriteExisting() {
        return overwriteExisting;
    }

    @Option(option = "verify-artifact-sources", description = "Require source/test-source validation before selecting source-code-url or test-code-url values.")
    public void setVerifyArtifactSourcesOption(boolean verifyArtifactSources) {
        this.verifyArtifactSources = verifyArtifactSources;
    }

    @Input
    public boolean isVerifyArtifactSources() {
        return verifyArtifactSources;
    }

    @TaskAction
    public void run() throws IOException, InterruptedException {
        String effectiveAgentCommand = effectiveAgentCommand();
        if (effectiveAgentCommand == null || effectiveAgentCommand.isBlank()) {
            throw new GradleException("Missing coding agent command. Provide --agent-command='<command>' or -PcodingAgentCommand='<command>'.");
        }

        String coordinateFilter = effectiveCoordinateFilter();
        List<String> coordinatesToProcess = resolveStrictCoordinates(coordinateFilter);
        coordinatesToProcess = coordinatesToProcess.stream()
                .filter(c -> !c.startsWith("samples:"))
                .sorted()
                .toList();

        if (coordinatesToProcess.isEmpty()) {
            getLogger().lifecycle("No matching coordinates found for filter '{}'.", coordinateFilter);
            return;
        }

        if (limit != null && limit <= 0) {
            throw new GradleException("Invalid --limit value '" + limit + "'. Must be >= 1.");
        }

        List<String> commandTokens = tokenizeCommand(effectiveAgentCommand);
        if (commandTokens.isEmpty()) {
            throw new GradleException("Invalid coding agent command: '" + effectiveAgentCommand + "'.");
        }

        boolean printPromptOnly = shouldPrintPromptOnly(commandTokens);
        File logsDir = null;
        if (!printPromptOnly) {
            logsDir = getProject().getLayout().getBuildDirectory().dir("agent-url-discovery-logs").get().getAsFile();
            if (!logsDir.exists() && !logsDir.mkdirs()) {
                throw new GradleException("Cannot create log directory: " + logsDir);
            }
        }

        int invoked = 0;
        int prompted = 0;
        int skipped = 0;
        for (String coordinate : coordinatesToProcess) {
            if (limit != null && (invoked + prompted) >= limit) {
                break;
            }
            TargetEntry target = resolveTargetEntry(coordinate);
            if (target == null) {
                skipped++;
                getLogger().warn("Skipping {} because no matching entry was found in index.json.", coordinate);
                continue;
            }
            if (!overwriteExisting
                    && hasAllCollectedValues(target.entry)
                    && !shouldBackfillMissingLanguage(target.entry, coordinateFilter)) {
                skipped++;
                getLogger().lifecycle("Skipping {} because all requested metadata fields are already present.", coordinate);
                continue;
            }

            String prompt = buildPrompt(target, overwriteExisting, verifyArtifactSources);
            if (printPromptOnly) {
                System.out.println(prompt);
                prompted++;
                continue;
            }

            List<String> invocation = buildInvocation(commandTokens, prompt);
            File logFile = new File(Objects.requireNonNull(logsDir), sanitizeCoordinate(coordinate) + ".log");

            int exit = runAndLogInvocation(coordinate, invocation, logFile);
            if (exit != 0) {
                throw new GradleException("Coding agent command failed for " + coordinate + " with exit code " + exit + ". See " + logFile);
            }
            invoked++;
        }

        getLogger().lifecycle(
                "Completed populateArtifactURLs: invoked={}, prompted={}, skipped={}, overwriteExisting={}, verifyArtifactSources={}, limit={}",
                invoked,
                prompted,
                skipped,
                overwriteExisting,
                verifyArtifactSources,
                limit
        );
    }

    private String effectiveCoordinateFilter() {
        if (hasText(coordinates)) {
            return coordinates.trim();
        }
        Object prop = getProject().findProperty("coordinates");
        return prop == null ? "" : prop.toString().trim();
    }

    private String effectiveAgentCommand() {
        if (hasText(agentCommand)) {
            return agentCommand.trim();
        }
        Object prop = getProject().findProperty("codingAgentCommand");
        if (prop == null) {
            prop = getProject().findProperty("agentCommand");
        }
        return prop == null ? null : prop.toString().trim();
    }

    private List<String> resolveStrictCoordinates(String filter) {
        TckExtension tck = Objects.requireNonNull(getProject().getExtensions().findByType(TckExtension.class));
        if (CoordinateUtils.isFractionalBatch(filter)) {
            int[] frac = CoordinateUtils.parseFraction(filter);
            List<String> allStrict = tck.getMatchingCoordinatesStrict("all");
            return CoordinateUtils.computeBatchedCoordinates(allStrict, frac[0], frac[1]);
        }
        return tck.getMatchingCoordinatesStrict(filter);
    }

    private TargetEntry resolveTargetEntry(String coordinate) throws IOException {
        String[] parts = coordinate.split(":");
        if (parts.length != 3) {
            throw new GradleException("Invalid coordinates '" + coordinate + "'. Expected group:artifact:version.");
        }

        String group = parts[0];
        String artifact = parts[1];
        String version = parts[2];

        Path indexPath = getProject().getProjectDir().toPath()
                .resolve("metadata")
                .resolve(group)
                .resolve(artifact)
                .resolve("index.json");
        if (!Files.exists(indexPath)) {
            throw new GradleException("Missing index.json for coordinates " + coordinate + " at " + indexPath);
        }

        List<MetadataVersionsIndexEntry> entries = OBJECT_MAPPER.readValue(indexPath.toFile(), new TypeReference<>() {});
        MetadataVersionsIndexEntry target = entries.stream()
                .filter(entry -> version.equals(entry.metadataVersion()))
                .findFirst()
                .orElse(null);

        if (target == null) {
            target = entries.stream()
                    .filter(entry -> entry.testedVersions() != null && entry.testedVersions().contains(version))
                    .findFirst()
                    .orElse(null);
        }

        if (target == null) {
            return null;
        }

        return new TargetEntry(group, artifact, version, indexPath, target);
    }

    private String buildPrompt(TargetEntry target, boolean overwriteExisting, boolean verifyArtifactSources) {
        String indexFile = target.indexPath.toString().replace('\\', '/');
        String metadataVersion = target.entry.metadataVersion();
        String testVersion = hasText(target.entry.testVersion()) ? target.entry.testVersion() : metadataVersion;
        String urlUpdateInstructions = urlUpdateInstructions(overwriteExisting, target.version);
        String sourceArtifactVerificationInstructions = sourceArtifactVerificationInstructions(verifyArtifactSources, target.version);

        return """
                Find the repository URL, the sources URL, the test suite URL, the documentation URL, and a concise two-sentence explanation for the following library: %s:%s:%s
                Also determine whether this is a language-specific library. If it is language-specific, set the "language" field using:
                - { "name": "kotlin", "version": "<kotlin major.minor, e.g. 2.0>" } for Kotlin libraries
                - { "name": "scala", "version": "2" } for Scala 2 libraries
                - { "name": "scala", "version": "3" } for Scala 3 libraries
                If the library is not language-specific, leave the "language" field absent.
                The sources URL, the test suite URL, and the documentation URL must be for the EXACT version "%s".
                The source, test suite, and documentation URLs should point to the right tag of the library.
                If we have these source of these artifacts on maven, that should be prefered.
                If there are tests on maven that should also be prefered.
                The "description" field must explain what the library does in exactly two sentences.
                %s

                Update this repository directly:
                - File: %s
                - Entry selector: "metadata-version" = "%s"
                %s
                - Set the "description" field to the selected two-sentence explanation.
                - Set the "language" field only when the library is language-specific.
                - If any of these URLs or the description cannot be found with confidence, set that field value to "N/A".
                - Do not use unversioned docs or "latest/current" docs.
                - Keep all other fields unchanged.
                - Keep JSON valid and consistently formatted.

                Current URL values:
                - source-code-url: %s
                - repository-url: %s
                - test-code-url: %s
                - documentation-url: %s
                - description: %s
                - language: %s

                Context:
                - Coordinate version: %s
                - Entry test-version: %s
                """.formatted(
                target.group,
                target.artifact,
                target.version,
                target.version,
                sourceArtifactVerificationInstructions,
                indexFile,
                metadataVersion,
                urlUpdateInstructions,
                currentValue(target.entry.sourceCodeUrl()),
                currentValue(target.entry.repositoryUrl()),
                currentValue(target.entry.testCodeUrl()),
                currentValue(target.entry.documentationUrl()),
                currentValue(target.entry.description()),
                currentLanguageValue(target.entry),
                target.version,
                testVersion
        ).strip();
    }

    private static String sourceArtifactVerificationInstructions(boolean verifyArtifactSources, String version) {
        if (!verifyArtifactSources) {
            return "";
        }
        return """
                Source Artifact Verification (required):
                - Verify candidate source URLs for version "%s", including Maven and non-Maven candidates.
                - If you use Maven source artifacts, confirm `-sources.jar` contains real source files (`.java`, `.kt`, `.scala`, `.groovy`), not only metadata/license files.
                - If you use Maven test-source artifacts, confirm `-test-sources.jar` contains real test source files.
                - For non-Maven source/test URLs (for example repository tree pages or downloadable archives), verify that the selected URL resolves to real source/test files for the exact version.
                - If a candidate source/test URL fails this verification, do not use it. Prefer a verified repository tag URL instead.
                """.formatted(version).strip();
    }

    private static String urlUpdateInstructions(boolean overwriteExisting, String version) {
        if (overwriteExisting) {
            return """
                    - Overwrite existing URL values.
                    - Set "source-code-url" to the selected source URL.
                    - Set "repository-url" to the selected repository URL.
                    - "repository-url" must be the canonical repository root URL and must not include a version/tag/branch path (for example, no "/tree/v_1.2.11").
                    - Set "test-code-url" to the selected test suite URL.
                    - Set "documentation-url" to the selected project documentation URL for version "%s".
                    - Set "description" to a concise explanation of the library in exactly two sentences.
                    - Set "language" to the structured language object when the library is language-specific; otherwise leave the field absent.
                    """.formatted(version).strip();
        }
        return """
                - Fill only missing fields among "source-code-url", "repository-url", "test-code-url", "documentation-url", "description", and "language".
                - A field is missing only when absent, null, or blank.
                - Do not modify fields that already contain a non-blank value.
                - Set missing "source-code-url" to the selected source URL.
                - Set missing "repository-url" to the selected repository URL.
                - "repository-url" must be the canonical repository root URL and must not include a version/tag/branch path (for example, no "/tree/v_1.2.11").
                - Set missing "test-code-url" to the selected test suite URL.
                - Set missing "documentation-url" to the selected project documentation URL for version "%s".
                - Set missing "description" to a concise explanation of the library in exactly two sentences.
                - Set missing "language" only when the library is language-specific; otherwise leave the field absent.
                """.formatted(version).strip();
    }

    private static String currentValue(String value) {
        return hasText(value) ? value : "<missing>";
    }

    private static String currentLanguageValue(MetadataVersionsIndexEntry entry) {
        if (entry.language() == null) {
            return "<missing>";
        }
        return "{\"name\": \"%s\", \"version\": \"%s\"}".formatted(entry.language().name(), entry.language().version());
    }

    private List<String> buildInvocation(List<String> commandTokens, String prompt) {
        List<String> invocation = new ArrayList<>(commandTokens.size() + 1);
        boolean replaced = false;
        for (String token : commandTokens) {
            if (token.contains(PROMPT_PLACEHOLDER)) {
                invocation.add(token.replace(PROMPT_PLACEHOLDER, prompt));
                replaced = true;
            } else {
                invocation.add(token);
            }
        }
        if (!replaced) {
            invocation.add(prompt);
        }
        return invocation;
    }

    private int runAndLogInvocation(String coordinate, List<String> invocation, File logFile) throws IOException, InterruptedException {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date());
        String header = "==== [" + timestamp + "] " + coordinate + System.lineSeparator() +
                "COMMAND: " + String.join(" ", invocation) + System.lineSeparator();
        Files.writeString(logFile.toPath(), header, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        ProcessBuilder processBuilder = new ProcessBuilder(invocation);
        processBuilder.directory(getProject().getProjectDir());
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        // We pass the prompt as CLI arguments, so close stdin to avoid commands
        // that read stdin (for example `codex exec`) blocking on EOF.
        process.getOutputStream().close();
        try (OutputStream logOutput = Files.newOutputStream(logFile.toPath(), StandardOpenOption.APPEND)) {
            TeeOutputStream teeOutputStream = new TeeOutputStream(logOutput, System.out);
            process.getInputStream().transferTo(teeOutputStream);
            teeOutputStream.flush();
        }
        int exit = process.waitFor();

        String footer = System.lineSeparator() + "EXIT_CODE: " + exit + System.lineSeparator();
        Files.writeString(logFile.toPath(), footer, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        getLogger().lifecycle("Ran agent for {} (exit {}). Log: {}", coordinate, exit, logFile);
        return exit;
    }

    private static List<String> tokenizeCommand(String command) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = COMMAND_TOKEN_PATTERN.matcher(command);
        while (matcher.find()) {
            String token = matcher.group(1);
            if (token == null) {
                token = matcher.group(2);
            }
            if (token == null) {
                token = matcher.group(3);
            }
            if (token != null && !token.isBlank()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean hasAllCollectedValues(MetadataVersionsIndexEntry entry) {
        return hasText(entry.sourceCodeUrl())
                && hasText(entry.repositoryUrl())
                && hasText(entry.testCodeUrl())
                && hasText(entry.documentationUrl())
                && hasText(entry.description());
    }

    private static boolean shouldBackfillMissingLanguage(MetadataVersionsIndexEntry entry, String coordinateFilter) {
        return hasText(coordinateFilter) && entry.language() == null;
    }

    private static boolean shouldPrintPromptOnly(List<String> commandTokens) {
        return OPENCODE_RUN_COMMAND.equals(commandTokens);
    }

    private static String sanitizeCoordinate(String coordinate) {
        return coordinate.replace(":", "-").replace("/", "-");
    }

    private record TargetEntry(
            String group,
            String artifact,
            String version,
            Path indexPath,
            MetadataVersionsIndexEntry entry
    ) {
    }
}
