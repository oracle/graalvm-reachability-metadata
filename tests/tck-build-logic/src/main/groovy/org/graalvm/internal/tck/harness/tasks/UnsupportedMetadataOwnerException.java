/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import org.gradle.api.GradleException;

/**
 * Signals a resolved dependency owner that repository metadata cannot yet host.
 * §FS-metadata
 */
final class UnsupportedMetadataOwnerException extends GradleException {
    private final String reason;
    private final String coordinate;

    UnsupportedMetadataOwnerException(String reason, String coordinate) {
        super(reason + ": " + coordinate);
        this.reason = reason;
        this.coordinate = coordinate;
    }

    String reason() {
        return reason;
    }

    String coordinate() {
        return coordinate;
    }
}
