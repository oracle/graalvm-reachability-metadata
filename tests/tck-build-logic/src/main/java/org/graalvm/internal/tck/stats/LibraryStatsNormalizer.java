/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.stats;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Canonical ordering and value normalization for persisted library stats.
 */
final class LibraryStatsNormalizer {

    private LibraryStatsNormalizer() {
    }

    static LibraryStatsModels.LibraryStats normalizeLibraryStats(LibraryStatsModels.LibraryStats libraryStats) {
        if (libraryStats == null || libraryStats.entries() == null) {
            return LibraryStatsSupport.emptyLibraryStats();
        }

        NavigableMap<String, LibraryStatsModels.ArtifactStats> entries = new TreeMap<>();
        for (Map.Entry<String, LibraryStatsModels.ArtifactStats> entry : libraryStats.entries().entrySet()) {
            String artifact = entry.getKey();
            LibraryStatsModels.ArtifactStats artifactStats = entry.getValue();
            if (artifactStats == null) {
                continue;
            }

            NavigableMap<String, LibraryStatsModels.MetadataVersionStats> metadataVersions = new TreeMap<>();
            if (artifactStats.metadataVersions() != null) {
                for (Map.Entry<String, LibraryStatsModels.MetadataVersionStats> metadataEntry : artifactStats.metadataVersions().entrySet()) {
                    metadataVersions.put(
                            metadataEntry.getKey(),
                            normalizeMetadataVersionStats(metadataEntry.getValue())
                    );
                }
            }

            entries.put(artifact, new LibraryStatsModels.ArtifactStats(metadataVersions));
        }

        return new LibraryStatsModels.LibraryStats(entries);
    }

    static LibraryStatsModels.MetadataVersionStats normalizeMetadataVersionStats(
            LibraryStatsModels.MetadataVersionStats metadataVersionStats
    ) {
        if (metadataVersionStats == null || metadataVersionStats.versions() == null) {
            return LibraryStatsSupport.emptyMetadataVersionStats();
        }
        NavigableMap<String, LibraryStatsModels.VersionStats> byVersion = new TreeMap<>();
        for (LibraryStatsModels.VersionStats versionStats : metadataVersionStats.versions()) {
            byVersion.put(versionStats.version(), normalizeVersionStats(versionStats));
        }
        return new LibraryStatsModels.MetadataVersionStats(new ArrayList<>(byVersion.values()));
    }

    private static LibraryStatsModels.VersionStats normalizeVersionStats(LibraryStatsModels.VersionStats versionStats) {
        if (versionStats == null) {
            return null;
        }

        return new LibraryStatsModels.VersionStats(
                versionStats.version(),
                normalizeDynamicAccessStatsValue(versionStats.dynamicAccess()),
                normalizeLibraryCoverage(versionStats.libraryCoverage())
        );
    }

    private static LibraryStatsModels.DynamicAccessStatsValue normalizeDynamicAccessStatsValue(
            LibraryStatsModels.DynamicAccessStatsValue dynamicAccess
    ) {
        if (dynamicAccess == null || !dynamicAccess.isAvailable()) {
            return LibraryStatsModels.DynamicAccessStatsValue.notAvailable();
        }

        Map<String, LibraryStatsModels.DynamicAccessBreakdown> normalizedBreakdown = new TreeMap<>();
        dynamicAccess.breakdown().forEach((reportType, breakdown) ->
                normalizedBreakdown.put(reportType, normalizeDynamicAccessBreakdown(breakdown))
        );

        return LibraryStatsModels.DynamicAccessStatsValue.available(new LibraryStatsModels.DynamicAccessStats(
                dynamicAccess.totalCalls(),
                dynamicAccess.coveredCalls(),
                normalizeRatio(dynamicAccess.coverageRatio()),
                normalizedBreakdown
        ));
    }

    private static LibraryStatsModels.DynamicAccessBreakdown normalizeDynamicAccessBreakdown(
            LibraryStatsModels.DynamicAccessBreakdown breakdown
    ) {
        if (breakdown == null) {
            return null;
        }

        return new LibraryStatsModels.DynamicAccessBreakdown(
                breakdown.totalCalls(),
                breakdown.coveredCalls(),
                normalizeRatio(breakdown.coverageRatio())
        );
    }

    private static LibraryStatsModels.LibraryCoverage normalizeLibraryCoverage(LibraryStatsModels.LibraryCoverage libraryCoverage) {
        if (libraryCoverage == null) {
            return null;
        }

        return new LibraryStatsModels.LibraryCoverage(
                normalizeCoverageMetricValue(libraryCoverage.line()),
                normalizeCoverageMetricValue(libraryCoverage.instruction()),
                normalizeCoverageMetricValue(libraryCoverage.method())
        );
    }

    private static LibraryStatsModels.CoverageMetricValue normalizeCoverageMetricValue(
            LibraryStatsModels.CoverageMetricValue coverageMetricValue
    ) {
        if (coverageMetricValue == null || !coverageMetricValue.isAvailable()) {
            return LibraryStatsModels.CoverageMetricValue.notAvailable();
        }

        BigDecimal ratio = coverageMetricValue.total() == 0L
                ? LibraryStatsSupport.fullyCoveredRatio()
                : normalizeRatio(coverageMetricValue.ratio());
        return LibraryStatsModels.CoverageMetricValue.available(new LibraryStatsModels.CoverageMetric(
                coverageMetricValue.covered(),
                coverageMetricValue.missed(),
                coverageMetricValue.total(),
                ratio
        ));
    }

    private static BigDecimal normalizeRatio(BigDecimal value) {
        if (value == null) {
            return null;
        }

        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() <= 0) {
            return normalized.setScale(1);
        }
        return normalized;
    }
}
