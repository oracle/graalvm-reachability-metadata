/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.graalvm.internal.tck.harness.TckExtension
import org.graalvm.internal.tck.utils.CoordinateUtils

import javax.inject.Inject

/**
 * Base task providing unified coordinate resolution from -Pcoordinates and optional overrides.
 * Supports:
 *  - single coordinate: group:artifact:version
 *  - filter by group/artifact
 *  - "all"
 *  - fractional batches "k/n"
 * Always filters out "samples:".
 */
abstract class CoordinatesAwareTask extends DefaultTask {

    protected final TckExtension tckExtension

    @Inject
    CoordinatesAwareTask() {
        this.tckExtension = project.extensions.findByType(TckExtension)
        // Default to no override
        getCoordinatesOverride().convention(Collections.<String>emptyList())
    }

    /**
     * Allows tasks (e.g., diff) to override the set of coordinates to run on.
     */
    @Input
    @Optional
    final ListProperty<String> coordinatesOverride = project.objects.listProperty(String)

    ListProperty<String> getCoordinatesOverride() {
        return coordinatesOverride
    }

    void setCoordinatesOverride(List<String> coords) {
        getCoordinatesOverride().set(coords)
    }

    protected List<String> resolveCoordinates() {
        List<String> override = getCoordinatesOverride().orNull
        List<String> coords
        if (override != null && !override.isEmpty()) {
            coords = override
        } else {
            String coordinateFilter = Objects.requireNonNullElse(project.findProperty("coordinates"), "") as String
            coords = computeMatchingCoordinates(coordinateFilter)
        }
        return coords.findAll { !it.startsWith("samples:") }
    }

    protected List<String> computeMatchingCoordinates(String filter) {
        if (CoordinateUtils.isFractionalBatch(filter)) {
            int[] frac = CoordinateUtils.parseFraction(filter)
            List<String> all = tckExtension.getMatchingCoordinates("all")
            return CoordinateUtils.computeBatchedCoordinates(all, frac[0], frac[1])
        } else {
            return tckExtension.getMatchingCoordinates(filter)
        }
    }
}
