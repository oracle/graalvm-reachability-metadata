/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.graalvm.internal.tck.harness.TckExtension;
import org.graalvm.internal.tck.utils.CoordinateUtils;

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
public abstract class CoordinatesAwareTask extends DefaultTask {

    protected final TckExtension tckExtension;

    @Input
    @Optional
    private final ListProperty<String> coordinatesOverride;

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
