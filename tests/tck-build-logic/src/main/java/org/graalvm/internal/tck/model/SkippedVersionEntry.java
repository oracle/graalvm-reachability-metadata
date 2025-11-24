/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.model;

/**
 * Represents a version of a library that should be skipped during testing,
 * along with the reason why this version is skipped.
 */
public record SkippedVersionEntry(
        String version,
        String reason
){}
