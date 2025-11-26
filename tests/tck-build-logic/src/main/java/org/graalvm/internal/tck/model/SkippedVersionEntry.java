package org.graalvm.internal.tck.model;

/**
 * Represents a version of a library that should be skipped during testing,
 * along with the reason why this version is skipped.
 */
public record SkippedVersionEntry(
        String version,
        String reason
){}
