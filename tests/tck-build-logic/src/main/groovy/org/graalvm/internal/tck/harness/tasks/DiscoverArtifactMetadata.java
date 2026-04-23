/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.graalvm.internal.tck.Coordinates;
import org.graalvm.internal.tck.model.DiscoveredArtifactMetadata;
import org.graalvm.internal.tck.utils.ArtifactMetadataDiscoveryUtils;

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
 * Resolves a single coordinate and asks an external coding agent to discover
 * repository URLs, a short description, and language-specific metadata.
 */
@SuppressWarnings("unused")
public class DiscoverArtifactMetadata extends DefaultTask {
    private static final Pattern COMMAND_TOKEN_PATTERN = Pattern.compile("\"([^\"]*)\"|'([^']*)'|(\\S+)");
    private static final List<String> OPENCODE_RUN_COMMAND = List.of("opencode", "run");
    private static final String PROMPT_PLACEHOLDER = "{prompt}";

    private String coordinates;
    private String agentCommand;
    private boolean overwriteExisting;

    @Option(option = "coordinates", description = "Coordinates in the form of group:artifact:version")
    public void setCoordinatesOption(String coordinates) {
        this.coordinates = coordinates;
    }

    @Input
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

    @Option(option = "overwrite-existing", description = "Overwrite an existing build-local discovery result if present.")
    public void setOverwriteExistingOption(boolean overwriteExisting) {
        this.overwriteExisting = overwriteExisting;
    }

    @Input
    public boolean isOverwriteExisting() {
        return overwriteExisting;
    }

    @TaskAction
    public void run() throws IOException, InterruptedException {
        Coordinates parsedCoordinates = Coordinates.parse(coordinates);
        String effectiveAgentCommand = effectiveAgentCommand();
        if (effectiveAgentCommand == null || effectiveAgentCommand.isBlank()) {
            throw new GradleException("Missing coding agent command. Provide --agent-command='<command>' or -PcodingAgentCommand='<command>'.");
        }

        Path discoveryFile = ArtifactMetadataDiscoveryUtils.discoveryFile(getProject().getLayout(), coordinates);
        if (Files.exists(discoveryFile) && !overwriteExisting) {
            getLogger().lifecycle("Skipping {} because discovered metadata already exists at {}.", coordinates, discoveryFile);
            return;
        }

        List<String> commandTokens = tokenizeCommand(effectiveAgentCommand);
        if (commandTokens.isEmpty()) {
            throw new GradleException("Invalid coding agent command: '" + effectiveAgentCommand + "'.");
        }

        String prompt = buildPrompt(parsedCoordinates, discoveryFile);
        if (shouldPrintPromptOnly(commandTokens)) {
            System.out.println(prompt);
            return;
        }

        ArtifactMetadataDiscoveryUtils.initializeDiscoveryFile(discoveryFile, coordinates);

        File logsDir = getProject().getLayout().getBuildDirectory().dir("agent-artifact-discovery-logs").get().getAsFile();
        if (!logsDir.exists() && !logsDir.mkdirs()) {
            Files.deleteIfExists(discoveryFile);
            throw new GradleException("Cannot create log directory: " + logsDir);
        }

        List<String> invocation = buildInvocation(commandTokens, prompt);
        File logFile = new File(logsDir, ArtifactMetadataDiscoveryUtils.sanitizeCoordinate(coordinates) + ".log");
        boolean success = false;
        try {
            int exit = runAndLogInvocation(invocation, logFile);
            if (exit != 0) {
                throw new GradleException("Coding agent command failed for " + coordinates + " with exit code " + exit + ". See " + logFile);
            }
            if (!Files.exists(discoveryFile)) {
                throw new GradleException("Expected discovered metadata file was not created: " + discoveryFile);
            }

            DiscoveredArtifactMetadata metadata = ArtifactMetadataDiscoveryUtils.readDiscoveryFile(discoveryFile);
            if (!coordinates.equals(metadata.coordinates())) {
                throw new GradleException("Discovered metadata file " + discoveryFile + " has unexpected coordinates: " + metadata.coordinates());
            }
            success = true;
        } finally {
            if (!success) {
                Files.deleteIfExists(discoveryFile);
            }
        }
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

    private String buildPrompt(Coordinates parsedCoordinates, Path discoveryFile) {
        return """
                Find the repository URL, the sources URL, the test suite URL, the documentation URL, and a concise two-sentence explanation for the following library: %s
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

                Update this file directly:
                - File: %s
                - Set "coordinates" to "%s".
                - Set "source-code-url", "repository-url", "test-code-url", and "documentation-url" to the discovered values.
                - Set "description" to the selected two-sentence explanation.
                - If any of these URLs or the description cannot be found with confidence, set that field value to "N/A".
                - Keep the JSON valid and consistently formatted.
                """.formatted(
                parsedCoordinates.toString(),
                parsedCoordinates.version(),
                discoveryFile.toString().replace('\\', '/'),
                parsedCoordinates.toString()
        ).strip();
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

    private int runAndLogInvocation(List<String> invocation, File logFile) throws IOException, InterruptedException {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date());
        String header = "==== [" + timestamp + "] " + coordinates + System.lineSeparator() +
                "COMMAND: " + String.join(" ", invocation) + System.lineSeparator();
        Files.writeString(logFile.toPath(), header, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        ProcessBuilder processBuilder = new ProcessBuilder(invocation);
        processBuilder.directory(getProject().getProjectDir());
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        process.getOutputStream().close();
        try (OutputStream logOutput = Files.newOutputStream(logFile.toPath(), StandardOpenOption.APPEND)) {
            TeeOutputStream teeOutputStream = new TeeOutputStream(logOutput, System.out);
            process.getInputStream().transferTo(teeOutputStream);
            teeOutputStream.flush();
        }
        int exit = process.waitFor();

        String footer = System.lineSeparator() + "EXIT_CODE: " + exit + System.lineSeparator();
        Files.writeString(logFile.toPath(), footer, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        getLogger().lifecycle("Ran agent for {} (exit {}). Log: {}", coordinates, exit, logFile);
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

    private static boolean shouldPrintPromptOnly(List<String> commandTokens) {
        return OPENCODE_RUN_COMMAND.equals(commandTokens);
    }
}
