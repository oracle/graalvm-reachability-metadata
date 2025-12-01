/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import org.graalvm.internal.tck.harness.TckExtension;
import org.graalvm.internal.tck.utils.CoordinateUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Base task providing unified coordinate resolution from -Pcoordinates and optional overrides.
 * Supports:
 *  - single coordinate: group:artifact:version
 *  - filter by group/artifact
 *  - "all"
 *  - fractional batches "k/n"
 * Always filters out "samples:".
 */
@SuppressWarnings("unused")
public abstract class CoordinatesAwareTask extends DefaultTask {

    protected final TckExtension tckExtension;

    private final ListProperty<String> coordinatesOverride;

    @Inject
    public CoordinatesAwareTask() {
        this.tckExtension = getProject().getExtensions().findByType(TckExtension.class);
        this.coordinatesOverride = getProject().getObjects().listProperty(String.class);
        // Default to no override
        this.coordinatesOverride.convention(Collections.emptyList());
    }

    /**
     * Allows tasks (e.g., diff) to override the set of coordinates to run on.
     */
    @Input
    @Optional
    public ListProperty<String> getCoordinatesOverride() {
        return coordinatesOverride;
    }

    public void setCoordinatesOverride(List<String> coords) {
        getCoordinatesOverride().set(coords);
    }

    /**
     * Resolves coordinates based on override or -Pcoordinates property.
     * Filters out entries starting with "samples:".
     */
    protected List<String> resolveCoordinates() throws GradleException {
        List<String> override = getCoordinatesOverride().getOrNull();
        final List<String> coords;
        if (override != null && !override.isEmpty()) {
            coords = override;
        } else {
            Object prop = getProject().findProperty("coordinates");
            String coordinateFilter = prop == null ? "" : prop.toString();
            coords = computeMatchingCoordinates(coordinateFilter);
        }
        return coords.stream()
                .filter(c -> !c.startsWith("samples:"))
                .collect(Collectors.toList());
    }

    protected List<String> computeMatchingCoordinates(String filter) {
        if (CoordinateUtils.isFractionalBatch(filter)) {
            int[] frac = CoordinateUtils.parseFraction(filter);
            List<String> all = tckExtension.getMatchingCoordinates("all");
            return CoordinateUtils.computeBatchedCoordinates(all, frac[0], frac[1]);
        } else {
            return tckExtension.getMatchingCoordinates(filter);
        }
    }
}
