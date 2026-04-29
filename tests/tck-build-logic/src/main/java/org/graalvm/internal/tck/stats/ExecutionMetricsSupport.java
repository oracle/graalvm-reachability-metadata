/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.stats;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gradle.api.GradleException;
import org.graalvm.internal.tck.model.MetadataVersionsIndexEntry;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Builds per-artifact execution metrics committed under {@code stats/}.
 */
public final class ExecutionMetricsSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private static final TypeReference<List<MetadataVersionsIndexEntry>> INDEX_ENTRIES_TYPE = new TypeReference<>() {
    };

    private static final Set<String> EXCLUDED_GROUP_IDS = Set.of("org.example", "samples");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int AVERAGE_SCALE = 6;

    private ExecutionMetricsSupport() {
    }

    public static ExecutionMetricsIndex buildIndex(
            Path statsRoot,
            Path metadataRoot
    ) {
        return buildIndex(statsRoot, metadataRoot, LocalDate.now(ZoneOffset.UTC));
    }

    public static ExecutionMetricsIndex buildIndex(
            Path statsRoot,
            Path metadataRoot,
            LocalDate snapshotDate
    ) {
        if (!Files.isDirectory(statsRoot)) {
            throw new GradleException("Missing stats root: " + statsRoot);
        }
        if (!Files.isDirectory(metadataRoot)) {
            throw new GradleException("Missing metadata root: " + metadataRoot);
        }

        LibraryStatsModels.LibraryStats libraryStats = LibraryStatsSupport.loadRepositoryStats(statsRoot);
        return new ExecutionMetricsIndex(
                snapshotDate.toString(),
                buildArtifactExecutionMetrics(snapshotDate.toString(), libraryStats, metadataRoot)
        );
    }

    public static void writeExecutionMetrics(Path statsRoot, ExecutionMetricsIndex executionMetricsIndex) {
        if (executionMetricsIndex == null) {
            throw new GradleException("Missing execution metrics index");
        }
        for (ArtifactExecutionMetrics executionMetrics : executionMetricsIndex.executions().values()) {
            LibraryStatsSupport.writeJson(
                    executionMetricsFile(statsRoot, executionMetrics.coordinate()),
                    executionMetrics
            );
        }
    }

    public static Path executionMetricsFile(Path statsRoot, String coordinate) {
        int separator = coordinate.indexOf(':');
        if (separator <= 0 || separator == coordinate.length() - 1) {
            throw new GradleException("Invalid execution metrics coordinate: " + coordinate);
        }
        return statsRoot
                .resolve(coordinate.substring(0, separator))
                .resolve(coordinate.substring(separator + 1))
                .resolve("execution-metrics.json");
    }

    private static Map<String, ArtifactExecutionMetrics> buildArtifactExecutionMetrics(
            String snapshotDate,
            LibraryStatsModels.LibraryStats libraryStats,
            Path metadataRoot
    ) {
        Map<String, ArtifactExecutionMetrics> metricsByCoordinate = new TreeMap<>();

        try (Stream<Path> paths = Files.find(metadataRoot, 3, (path, attrs) -> {
            if (!attrs.isRegularFile()) {
                return false;
            }
            Path relative = metadataRoot.relativize(path);
            if (relative.getNameCount() != 3) {
                return false;
            }
            String groupId = relative.getName(0).toString();
            return "index.json".equals(relative.getName(2).toString()) && !EXCLUDED_GROUP_IDS.contains(groupId);
        })) {
            for (Path indexFile : paths.sorted().toList()) {
                Path relative = metadataRoot.relativize(indexFile);
                String groupId = relative.getName(0).toString();
                String artifactId = relative.getName(1).toString();
                String coordinate = groupId + ":" + artifactId;
                List<MetadataVersionsIndexEntry> indexEntries = readIndexEntries(indexFile);
                LibraryStatsModels.ArtifactStats artifactStats = libraryStats.entries() == null
                        ? null
                        : libraryStats.entries().get(coordinate);
                metricsByCoordinate.put(
                        coordinate,
                        buildArtifactExecutionMetrics(snapshotDate, coordinate, indexEntries, artifactStats)
                );
            }
        } catch (IOException e) {
            throw new GradleException("Failed to traverse metadata root " + metadataRoot, e);
        }

        return metricsByCoordinate;
    }

    private static ArtifactExecutionMetrics buildArtifactExecutionMetrics(
            String snapshotDate,
            String coordinate,
            List<MetadataVersionsIndexEntry> indexEntries,
            LibraryStatsModels.ArtifactStats artifactStats
    ) {
        CodeCoverageMetrics codeCoverage = aggregateCodeCoverage(artifactStats);
        return new ArtifactExecutionMetrics(
                snapshotDate,
                coordinate,
                selectDescription(indexEntries),
                selectLatestMetadataVersion(indexEntries),
                indexEntries == null ? 0 : indexEntries.size(),
                countTestedVersions(indexEntries),
                hasStats(artifactStats),
                aggregateDynamicAccessCoverage(artifactStats),
                codeCoverage,
                Math.toIntExact(testedLinesOfCode(codeCoverage))
        );
    }

    private static String selectDescription(List<MetadataVersionsIndexEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return "";
        }
        for (MetadataVersionsIndexEntry entry : entries) {
            if (entry != null && Boolean.TRUE.equals(entry.latest()) && !isBlank(entry.description())) {
                return entry.description();
            }
        }
        for (MetadataVersionsIndexEntry entry : entries) {
            if (entry != null && !isBlank(entry.description())) {
                return entry.description();
            }
        }
        return "";
    }

    private static String selectLatestMetadataVersion(List<MetadataVersionsIndexEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return "";
        }
        for (MetadataVersionsIndexEntry entry : entries) {
            if (entry != null && Boolean.TRUE.equals(entry.latest()) && !isBlank(entry.metadataVersion())) {
                return entry.metadataVersion();
            }
        }
        for (MetadataVersionsIndexEntry entry : entries) {
            if (entry != null && !isBlank(entry.metadataVersion())) {
                return entry.metadataVersion();
            }
        }
        return "";
    }

    private static int countTestedVersions(List<MetadataVersionsIndexEntry> entries) {
        int testedVersions = 0;
        if (entries == null) {
            return testedVersions;
        }
        for (MetadataVersionsIndexEntry entry : entries) {
            if (entry != null && entry.testedVersions() != null) {
                testedVersions += entry.testedVersions().size();
            }
        }
        return testedVersions;
    }

    private static boolean hasStats(LibraryStatsModels.ArtifactStats artifactStats) {
        if (artifactStats == null || artifactStats.metadataVersions() == null) {
            return false;
        }
        for (LibraryStatsModels.MetadataVersionStats metadataVersionStats : artifactStats.metadataVersions().values()) {
            if (metadataVersionStats != null
                    && metadataVersionStats.versions() != null
                    && !metadataVersionStats.versions().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static DynamicAccessExecutionMetrics aggregateDynamicAccessCoverage(LibraryStatsModels.ArtifactStats artifactStats) {
        if (artifactStats == null || artifactStats.metadataVersions() == null) {
            return DynamicAccessExecutionMetrics.notAvailable();
        }

        BigDecimal ratioSum = BigDecimal.ZERO;
        long ratioCount = 0;
        long coveredCalls = 0;
        long totalCalls = 0;
        Map<String, DynamicAccessAccumulator> breakdownAccumulators = new TreeMap<>();
        for (LibraryStatsModels.MetadataVersionStats metadataVersionStats : artifactStats.metadataVersions().values()) {
            if (metadataVersionStats == null || metadataVersionStats.versions() == null) {
                continue;
            }
            for (LibraryStatsModels.VersionStats versionStats : metadataVersionStats.versions()) {
                if (versionStats == null || versionStats.dynamicAccess() == null || !versionStats.dynamicAccess().isAvailable()) {
                    continue;
                }
                ratioSum = ratioSum.add(versionStats.dynamicAccess().coverageRatio());
                ratioCount++;
                coveredCalls += versionStats.dynamicAccess().coveredCalls();
                totalCalls += versionStats.dynamicAccess().totalCalls();
                if (versionStats.dynamicAccess().breakdown() != null) {
                    versionStats.dynamicAccess().breakdown().forEach((reportType, breakdown) -> {
                        if (breakdown != null) {
                            breakdownAccumulators.computeIfAbsent(reportType, ignored -> new DynamicAccessAccumulator())
                                    .add(breakdown.coveredCalls(), breakdown.totalCalls());
                        }
                    });
                }
            }
        }

        if (ratioCount == 0) {
            return DynamicAccessExecutionMetrics.notAvailable();
        }

        Map<String, DynamicAccessBreakdownExecutionMetrics> breakdown = new TreeMap<>();
        for (Map.Entry<String, DynamicAccessAccumulator> entry : breakdownAccumulators.entrySet()) {
            DynamicAccessAccumulator accumulator = entry.getValue();
            breakdown.put(
                    entry.getKey(),
                    new DynamicAccessBreakdownExecutionMetrics(
                            dynamicAccessCoveragePercent(
                                    accumulator.coveredCalls(),
                                    accumulator.totalCalls(),
                                    BigDecimal.ONE,
                                    1
                            ),
                            accumulator.coveredCalls(),
                            accumulator.totalCalls()
                    )
            );
        }
        return new DynamicAccessExecutionMetrics(
                dynamicAccessCoveragePercent(coveredCalls, totalCalls, ratioSum, ratioCount),
                coveredCalls,
                totalCalls,
                true,
                breakdown
        );
    }

    private static CodeCoverageMetrics aggregateCodeCoverage(LibraryStatsModels.ArtifactStats artifactStats) {
        CoverageAccumulator line = new CoverageAccumulator();
        CoverageAccumulator instruction = new CoverageAccumulator();
        CoverageAccumulator method = new CoverageAccumulator();
        if (artifactStats != null && artifactStats.metadataVersions() != null) {
            for (LibraryStatsModels.MetadataVersionStats metadataVersionStats : artifactStats.metadataVersions().values()) {
                if (metadataVersionStats == null || metadataVersionStats.versions() == null) {
                    continue;
                }
                for (LibraryStatsModels.VersionStats versionStats : metadataVersionStats.versions()) {
                    if (versionStats == null || versionStats.libraryCoverage() == null) {
                        continue;
                    }
                    line.add(versionStats.libraryCoverage().line());
                    instruction.add(versionStats.libraryCoverage().instruction());
                    method.add(versionStats.libraryCoverage().method());
                }
            }
        }
        return new CodeCoverageMetrics(
                line.toCoverageMetricSummary(),
                instruction.toCoverageMetricSummary(),
                method.toCoverageMetricSummary()
        );
    }

    private static long testedLinesOfCode(CodeCoverageMetrics codeCoverage) {
        return codeCoverage.line().available()
                ? codeCoverage.line().covered()
                : 0L;
    }

    private static BigDecimal dynamicAccessCoveragePercent(
            long coveredCalls,
            long totalCalls,
            BigDecimal ratioSum,
            long ratioCount
    ) {
        if (totalCalls > 0) {
            return BigDecimal.valueOf(coveredCalls)
                    .divide(BigDecimal.valueOf(totalCalls), AVERAGE_SCALE, RoundingMode.HALF_UP)
                    .multiply(HUNDRED)
                    .setScale(1, RoundingMode.HALF_UP);
        }
        return ratioSum
                .divide(BigDecimal.valueOf(ratioCount), AVERAGE_SCALE, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .setScale(1, RoundingMode.HALF_UP);
    }

    private static BigDecimal coverageRatio(long covered, long total) {
        if (total == 0L) {
            return BigDecimal.ONE.setScale(1, RoundingMode.HALF_UP);
        }
        BigDecimal ratio = BigDecimal.valueOf(covered)
                .divide(BigDecimal.valueOf(total), AVERAGE_SCALE, RoundingMode.HALF_UP)
                .stripTrailingZeros();
        if (ratio.scale() <= 0) {
            return ratio.setScale(1);
        }
        return ratio;
    }

    private static List<MetadataVersionsIndexEntry> readIndexEntries(Path indexFile) {
        try {
            return OBJECT_MAPPER.readValue(indexFile.toFile(), INDEX_ENTRIES_TYPE);
        } catch (IOException e) {
            throw new GradleException("Failed to read metadata index file " + indexFile, e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record ExecutionMetricsIndex(
            String date,
            Map<String, ArtifactExecutionMetrics> executions
    ) {
    }

    public record ArtifactExecutionMetrics(
            String date,
            String coordinate,
            String description,
            String latestMetadataVersion,
            int metadataBaselines,
            int testedVersions,
            boolean executionStatsAvailable,
            DynamicAccessExecutionMetrics dynamicAccess,
            CodeCoverageMetrics codeCoverage,
            int testedLinesOfCode
    ) {
    }

    public record DynamicAccessExecutionMetrics(
            BigDecimal coveragePercent,
            long coveredCalls,
            long totalCalls,
            boolean available,
            Map<String, DynamicAccessBreakdownExecutionMetrics> breakdown
    ) {

        static DynamicAccessExecutionMetrics notAvailable() {
            return new DynamicAccessExecutionMetrics(BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP), 0, 0, false, Map.of());
        }
    }

    public record DynamicAccessBreakdownExecutionMetrics(
            BigDecimal coveragePercent,
            long coveredCalls,
            long totalCalls
    ) {
    }

    public record CodeCoverageMetrics(
            CoverageMetricSummary line,
            CoverageMetricSummary instruction,
            CoverageMetricSummary method
    ) {
    }

    public record CoverageMetricSummary(
            long covered,
            long missed,
            long total,
            BigDecimal coverageRatio,
            boolean available
    ) {

        static CoverageMetricSummary notAvailable() {
            return new CoverageMetricSummary(0, 0, 0, BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP), false);
        }
    }

    private static final class DynamicAccessAccumulator {

        private long coveredCalls;
        private long totalCalls;

        private void add(long covered, long total) {
            coveredCalls += covered;
            totalCalls += total;
        }

        private long coveredCalls() {
            return coveredCalls;
        }

        private long totalCalls() {
            return totalCalls;
        }
    }

    private static final class CoverageAccumulator {

        private long covered;
        private long missed;
        private long total;
        private int count;

        private void add(LibraryStatsModels.CoverageMetricValue metric) {
            if (metric == null || !metric.isAvailable()) {
                return;
            }
            covered += metric.covered();
            missed += metric.missed();
            total += metric.total();
            count++;
        }

        private CoverageMetricSummary toCoverageMetricSummary() {
            if (count == 0) {
                return CoverageMetricSummary.notAvailable();
            }
            return new CoverageMetricSummary(
                    covered,
                    missed,
                    total,
                    coverageRatio(covered, total),
                    true
            );
        }
    }
}
