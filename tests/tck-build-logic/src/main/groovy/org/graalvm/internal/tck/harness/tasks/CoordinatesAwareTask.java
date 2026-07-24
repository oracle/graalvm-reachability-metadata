/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import org.gradle.api.GradleException;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.graalvm.internal.tck.harness.TckExtension;
import org.graalvm.internal.tck.utils.CoordinateUtils;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Base task providing unified coordinate resolution from -Pcoordinates and optional overrides.
 * Supports:
 *  - single coordinate: group:artifact:version
 *  - filter by group/artifact
 *  - "all"
 *  - fractional batches "k/n"
 * Fixture coordinates remain runnable; reporting tasks decide which coordinates
 * are excluded from supported-library outputs. §TCK-test-harness.1
 */
public abstract class CoordinatesAwareTask extends DefaultTask {
    private static final String EXCLUDED_COORDINATES_FILE_PROPERTY = "tck.excludedCoordinatesFile";

    protected final TckExtension tckExtension;

    @Input
    @Optional
    private final ListProperty<String> coordinatesOverride;
    private int excludedCoordinateCount;

    @Inject
    public CoordinatesAwareTask() {
        this.tckExtension = getProject().getExtensions().findByType(TckExtension.class);
        this.coordinatesOverride = getProject().getObjects().listProperty(String.class);
        this.coordinatesOverride.convention(Collections.emptyList());
    }

    public ListProperty<String> getCoordinatesOverride() {
        return coordinatesOverride;
    }

    public void setCoordinatesOverride(List<String> coords) {
        getCoordinatesOverride().set(coords);
    }

    protected List<String> resolveCoordinates() {
        List<String> override = getCoordinatesOverride().getOrNull();
        List<String> coords;
        if (override != null && !override.isEmpty()) {
            coords = override;
        } else {
            String coordinateFilter = Objects.toString(getProject().findProperty("coordinates"), "");
            coords = computeMatchingCoordinates(coordinateFilter);
        }
        Set<String> excludedLibraries = excludedLibraries();
        List<String> includedCoordinates = coords.stream()
                .filter(c -> !excludedLibraries.contains(libraryKey(c)))
                .collect(Collectors.toList());
        excludedCoordinateCount = coords.size() - includedCoordinates.size();
        if (excludedCoordinateCount > 0) {
            getLogger().lifecycle("Excluded {} coordinate(s) using {}.", excludedCoordinateCount,
                    EXCLUDED_COORDINATES_FILE_PROPERTY);
        }
        return includedCoordinates;
    }

    protected boolean excludedCoordinates() {
        return excludedCoordinateCount > 0;
    }

    protected List<String> computeMatchingCoordinates(String filter) {
        if (CoordinateUtils.isFractionalBatch(filter)) {
            int[] frac = CoordinateUtils.parseFraction(filter);
            List<String> all = tckExtension.getMatchingCoordinatesStrict("all");
            return CoordinateUtils.computeBatchedCoordinates(all, frac[0], frac[1]);
        } else {
            return tckExtension.getMatchingCoordinates(filter);
        }
    }

    private Set<String> excludedLibraries() {
        Object exclusionsPath = getProject().findProperty(EXCLUDED_COORDINATES_FILE_PROPERTY);
        if (exclusionsPath == null || exclusionsPath.toString().isBlank()) {
            return Collections.emptySet();
        }

        File exclusionsFile = getProject().file(exclusionsPath.toString());
        if (!exclusionsFile.isFile()) {
            throw new GradleException("Coordinate exclusion file does not exist: " + exclusionsFile);
        }
        try {
            return Files.readAllLines(exclusionsFile.toPath(), StandardCharsets.UTF_8).stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(CoordinatesAwareTask::libraryKey)
                    .collect(Collectors.toSet());
        } catch (IOException exception) {
            throw new GradleException("Cannot read coordinate exclusion file: " + exclusionsFile, exception);
        }
    }

    private static String libraryKey(String coordinates) {
        String[] parts = coordinates.split(":", -1);
        if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new GradleException("Invalid coordinate exclusion '" + coordinates + "'. Expected group:artifact.");
        }
        return parts[0] + ":" + parts[1];
    }
}
