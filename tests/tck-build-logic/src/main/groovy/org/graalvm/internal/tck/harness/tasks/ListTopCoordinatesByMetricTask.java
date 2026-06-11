/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.graalvm.internal.tck.stats.LibraryStatsModels;
import org.graalvm.internal.tck.stats.LibraryStatsSupport;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * Lists top coordinates from committed library stats.
 * <p>
 * Implements §TCK-test-harness.8 — the {@code listTopCoordinatesByMetric} reporting task.
 */
@SuppressWarnings("unused")
public abstract class ListTopCoordinatesByMetricTask extends DefaultTask {

    private static final int DEFAULT_LIMIT = 256;

    @Input
    public abstract Property<@NotNull String> getMetric();

    @Input
    public abstract Property<@NotNull Integer> getLimit();

    public ListTopCoordinatesByMetricTask() {
        getMetric().convention(getProject().getProviders()
                .gradleProperty("metric")
                .orElse(LibraryStatsSupport.DYNAMIC_ACCESSES_METRIC));
        getLimit().convention(getProject().getProviders()
                .gradleProperty("limit")
                .orElse(getProject().getProviders().gradleProperty("topN"))
                .map(Integer::parseInt)
                .orElse(DEFAULT_LIMIT));
    }

    @Option(option = "metric", description = "Library stats metric to rank by. Supported: dynamic-accesses")
    public void setMetricOption(String value) {
        getMetric().set(value);
    }

    @Option(option = "limit", description = "Maximum number of coordinates to print")
    public void setLimitOption(String value) {
        try {
            getLimit().set(Integer.parseInt(value));
        } catch (NumberFormatException e) {
            throw new GradleException("Property 'limit' must be an integer. Got: " + value, e);
        }
    }

    @TaskAction
    public void list() {
        Path statsRoot = getProject().getRootProject().getProjectDir().toPath().resolve("stats");
        List<LibraryStatsModels.CoordinateMetric> topCoordinates = LibraryStatsSupport.topCoordinatesByMetric(
                LibraryStatsSupport.loadRepositoryStats(statsRoot),
                getMetric().get(),
                getLimit().get()
        );
        List<String> coordinates = topCoordinates.stream()
                .map(LibraryStatsModels.CoordinateMetric::coordinate)
                .toList();
        coordinates.forEach(coordinate -> getLogger().quiet(coordinate));
        writeGithubOutput("coordinates", String.join(" ", coordinates));
    }

    private void writeGithubOutput(String key, String value) {
        String outputPath = System.getenv("GITHUB_OUTPUT");
        if (outputPath == null || outputPath.isBlank()) {
            return;
        }
        try {
            Files.writeString(
                    Path.of(outputPath),
                    key + "=" + value + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            throw new GradleException("Failed to write GitHub output " + key + " to " + outputPath, e);
        }
    }
}
