/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;
import org.gradle.process.ExecSpec;
import org.graalvm.internal.tck.Coordinates;
import org.graalvm.internal.tck.stats.LibraryStatsSupport;
import org.graalvm.internal.tck.utils.DynamicAccessUtils;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves an arbitrary Maven coordinate and computes dynamic-access totals without repo-tracked tests.
 */
@SuppressWarnings("unused")
public abstract class AnalyzeExternalLibraryDynamicAccessTask extends org.gradle.api.DefaultTask {

    @Input
    @Optional
    public abstract Property<@NotNull String> getCoordinates();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @Inject
    public abstract ExecOperations getExecOperations();

    @Option(option = "coordinates", description = "External Maven coordinate in group:artifact:version form")
    public void setCoordinatesOption(String value) {
        getCoordinates().set(value);
    }

    @Inject
    public AnalyzeExternalLibraryDynamicAccessTask() {
        getOutputFile().convention(
                getProject().getLayout().getBuildDirectory().file(
                        "reports/external-dynamic-access/summary.json"
                )
        );
    }

    @TaskAction
    public void analyze() {
        String coordinatesValue = effectiveCoordinates();
        Coordinates coordinates = Coordinates.parse(coordinatesValue);
        ResolvedExternalLibrary resolvedLibrary = resolveLibrary(coordinates);

        Path rootDir = getProject().getLayout().getBuildDirectory()
                .dir("tmp/external-dynamic-access/" + sanitize(coordinates.toString()))
                .get()
                .getAsFile()
                .toPath();
        Path sourceDir = rootDir.resolve("src");
        Path classesDir = rootDir.resolve("classes");
        Path imageDir = rootDir.resolve("image");

        try {
            Files.createDirectories(sourceDir);
            Files.createDirectories(classesDir);
            Files.createDirectories(imageDir);
            Files.writeString(
                    sourceDir.resolve("Main.java"),
                    """
                    public class Main {
                        public static void main(String[] args) {
                        }
                    }
                    """,
                    StandardCharsets.UTF_8
            );
        } catch (IOException e) {
            throw new GradleException("Failed to create external dynamic access analysis sources", e);
        }

        runCommand(
                List.of("javac", "-d", classesDir.toString(), sourceDir.resolve("Main.java").toString()),
                rootDir,
                false,
                "Failed to compile external dynamic access entry point"
        );

        List<String> classpathEntries = new ArrayList<>();
        classpathEntries.add(classesDir.toString());
        classpathEntries.addAll(resolvedLibrary.classpath().stream().map(path -> path.toAbsolutePath().toString()).toList());

        List<String> nativeImageCommand = new ArrayList<>();
        nativeImageCommand.add(resolveNativeImageExecutable());
        nativeImageCommand.add("-H:+UnlockExperimentalVMOptions");
        nativeImageCommand.add("-H:Path=" + imageDir);
        nativeImageCommand.add("-H:Name=analysis");
        nativeImageCommand.add("-H:Class=Main");
        nativeImageCommand.add("-cp");
        nativeImageCommand.add(String.join(java.io.File.pathSeparator, classpathEntries));
        nativeImageCommand.addAll(DynamicAccessUtils.buildArgsForClasspathEntries(resolvedLibrary.rootJars().stream().map(Path::toFile).toList()));
        nativeImageCommand.add("--no-fallback");

        runCommand(
                nativeImageCommand,
                rootDir,
                true,
                "Failed to compute external dynamic access report for " + coordinates
        );

        LibraryStatsSupport.ExternalDynamicAccessSummary summary = LibraryStatsSupport.buildExternalDynamicAccessSummary(
                resolvedLibrary.rootJars(),
                imageDir.resolve("dynamic-access")
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("coordinate", coordinates.toString());
        result.put("totalCalls", summary.totalCalls());
        result.put("breakdown", summary.breakdown());

        Path output = getOutputFile().get().getAsFile().toPath();
        try {
            Files.createDirectories(output.getParent());
            Files.writeString(output, LibraryStatsSupport.toJsonString(result) + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new GradleException("Failed to write external dynamic access summary to " + output, e);
        }

        getLogger().quiet(LibraryStatsSupport.toJsonString(result));
    }

    private String effectiveCoordinates() {
        String optionValue = getCoordinates().getOrNull();
        if (optionValue != null && !optionValue.isBlank()) {
            return optionValue;
        }
        Object property = getProject().findProperty("coordinates");
        if (property != null && !property.toString().isBlank()) {
            return property.toString();
        }
        throw new GradleException("Missing required coordinates for external dynamic access analysis.");
    }

    private ResolvedExternalLibrary resolveLibrary(Coordinates coordinates) {
        DependencyHandler dependencies = getProject().getDependencies();
        Configuration configuration = getProject().getConfigurations().detachedConfiguration(
                dependencies.create(coordinates.toString())
        );
        configuration.setTransitive(true);

        Set<Path> classpath = configuration.resolve().stream()
                .map(file -> file.toPath().toAbsolutePath())
                .sorted(Comparator.comparing(Path::toString))
                .collect(LinkedHashSet::new, Set::add, Set::addAll);

        List<Path> rootJars = configuration.getResolvedConfiguration().getResolvedArtifacts().stream()
                .filter(artifact -> matchesRequestedArtifact(artifact, coordinates))
                .map(artifact -> artifact.getFile().toPath().toAbsolutePath())
                .sorted(Comparator.comparing(Path::toString))
                .toList();

        if (rootJars.isEmpty()) {
            throw new GradleException("Failed to resolve root artifact JARs for " + coordinates);
        }

        return new ResolvedExternalLibrary(rootJars, new ArrayList<>(classpath));
    }

    private boolean matchesRequestedArtifact(ResolvedArtifact artifact, Coordinates coordinates) {
        String artifactGroup = artifact.getModuleVersion().getId().getGroup();
        String artifactName = artifact.getModuleVersion().getId().getName();
        String artifactVersion = artifact.getModuleVersion().getId().getVersion();

        if (coordinates.group().equals(artifactGroup) && coordinates.artifact().equals(artifactName) && coordinates.version().equals(artifactVersion)) {
            return true;
        }
        return coordinates.artifact().equals(artifactName) && coordinates.version().equals(artifactVersion);
    }

    private void runCommand(List<String> command, Path workingDirectory, boolean streamOutput, String errorPrefix) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        ExecResult execResult = getExecOperations().exec((ExecSpec spec) -> {
            spec.commandLine(command);
            spec.workingDir(workingDirectory.toFile());
            spec.setIgnoreExitValue(true);
            if (streamOutput) {
                spec.setStandardOutput(new TeeOutputStream(stdout, System.out));
                spec.setErrorOutput(new TeeOutputStream(stderr, System.err));
            } else {
                spec.setStandardOutput(stdout);
                spec.setErrorOutput(stderr);
            }
        });
        if (execResult.getExitValue() != 0) {
            throw new GradleException(errorPrefix + System.lineSeparator() + stderr.toString(StandardCharsets.UTF_8));
        }
    }

    private String resolveNativeImageExecutable() {
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isBlank()) {
            Path candidate = Path.of(javaHome).resolve("bin").resolve("native-image");
            if (Files.isRegularFile(candidate)) {
                return candidate.toString();
            }
        }
        return "native-image";
    }

    private String sanitize(String input) {
        return input.replace(':', '_').replace('/', '_');
    }

    private record ResolvedExternalLibrary(List<Path> rootJars, List<Path> classpath) {
    }
}
