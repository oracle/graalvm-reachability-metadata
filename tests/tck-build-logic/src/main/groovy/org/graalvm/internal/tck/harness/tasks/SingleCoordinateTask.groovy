/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks

import org.gradle.api.GradleException

/**
 * Base task for actions that must operate on exactly one coordinate.
 * Reuses the same unified resolution as CoordinatesAwareTask and enforces a single result.
 */
abstract class SingleCoordinateTask extends CoordinatesAwareTask {

    /**
     * Resolves to exactly one coordinate or fails with a helpful error.
     */
    protected String resolveSingleCoordinate() {
        List<String> coords = resolveCoordinates()
        if (coords.isEmpty()) {
            throw new GradleException("No matching coordinates found. Provide a concrete coordinate via -Pcoordinates=group:artifact:version")
        }
        if (coords.size() > 1) {
            throw new GradleException("Multiple coordinates matched: ${coords}. This task requires a single concrete coordinate. " +
                    "Please specify an exact 'group:artifact:version' using -Pcoordinates.")
        }
        return coords.get(0)
    }
}
