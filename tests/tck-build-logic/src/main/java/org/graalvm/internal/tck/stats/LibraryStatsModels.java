/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.stats;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Strongly-typed models for artifact-indexed library stats JSON payloads.
 */
public final class LibraryStatsModels {

    private LibraryStatsModels() {
    }

    public record LibraryStats(
            Map<String, ArtifactStats> entries
    ) {
    }

    public record ArtifactStats(
            String artifactId,
            Map<String, MetadataVersionStats> metadataVersions
    ) {
    }

    public record MetadataVersionStats(
            List<VersionStats> versions
    ) {
    }

    public record VersionStats(
            String coordinate,
            String version,
            DynamicAccessStats dynamicAccess,
            LibraryCoverage libraryCoverage
    ) {
    }

    public record DynamicAccessStats(
            long totalCalls,
            long coveredCalls,
            BigDecimal coverageRatio,
            Map<String, DynamicAccessBreakdown> breakdown
    ) {
    }

    public record DynamicAccessBreakdown(
            long totalCalls,
            long coveredCalls,
            BigDecimal coverageRatio
    ) {
    }

    public record LibraryCoverage(
            CoverageMetric line,
            CoverageMetric instruction,
            CoverageMetric method
    ) {
    }

    public record CoverageMetric(
            long covered,
            long missed,
            long total,
            BigDecimal ratio
    ) {
    }
}
