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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Builds the generated JSON payload used by README shields.io badges.
 */
public final class ReadmeBadgeSummarySupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private static final TypeReference<List<MetadataVersionsIndexEntry>> INDEX_ENTRIES_TYPE = new TypeReference<>() {
    };

    static final Set<String> EXCLUDED_GROUP_IDS = Set.of("org.example", "samples");
    static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    static final int AVERAGE_SCALE = 6;

    private ReadmeBadgeSummarySupport() {
    }

    public static ReadmeBadgeSummary buildSummary(
            Path statsRoot,
            Path metadataRoot
    ) {
        return buildSummary(statsRoot, metadataRoot, LocalDate.now(ZoneOffset.UTC));
    }

    public static ReadmeBadgeSummary buildSummary(
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

        StatsMetrics statsMetrics = buildStatsMetrics(libraryStats);
        MetadataIndexMetrics metadataIndexMetrics = buildMetadataIndexMetrics(metadataRoot);

        return new ReadmeBadgeSummary(
                snapshotDate.toString(),
                new BadgeValues(
                        MetricsChartSupport.formatInteger(metadataIndexMetrics.metadataIndexes()),
                        MetricsChartSupport.formatPercent(statsMetrics.dynamicAccessCallCoveragePercent()),
                        MetricsChartSupport.formatInteger(metadataIndexMetrics.testedVersions()),
                        MetricsChartSupport.formatInteger(statsMetrics.testedLinesOfCode())
                ),
                new Metrics(
                        metadataIndexMetrics,
                        statsMetrics
                )
        );
    }

    public static void writeSummary(Path summaryFile, ReadmeBadgeSummary summary) {
        writeJsonWithTrailingNewline(summaryFile, summary, "Failed to write README badge summary to ");
    }

    public static ReadmeMetricsHistory loadHistory(Path historyFile) {
        if (!Files.isRegularFile(historyFile)) {
            return new ReadmeMetricsHistory(List.of());
        }
        try {
            return OBJECT_MAPPER.readValue(historyFile.toFile(), ReadmeMetricsHistory.class);
        } catch (IOException e) {
            throw new GradleException("Failed to read README metrics history from " + historyFile, e);
        }
    }

    public static ReadmeMetricsHistory withSnapshot(ReadmeMetricsHistory history, ReadmeBadgeSummary summary) {
        TreeMap<String, HistoryMetrics> byDate = new TreeMap<>();
        if (history != null && history.history() != null) {
            for (HistoryEntry entry : history.history()) {
                if (entry != null && entry.date() != null && entry.metrics() != null) {
                    byDate.put(entry.date(), entry.metrics());
                }
            }
        }
        byDate.put(summary.date(), new HistoryMetrics(
                summary.metrics().metadataIndexes(),
                summary.metrics().stats()
        ));

        List<HistoryEntry> entries = new ArrayList<>();
        for (var item : byDate.entrySet()) {
            entries.add(new HistoryEntry(item.getKey(), item.getValue()));
        }
        return new ReadmeMetricsHistory(entries);
    }

    public static void writeHistory(Path historyFile, ReadmeMetricsHistory history) {
        writeJsonWithTrailingNewline(historyFile, history, "Failed to write README metrics history to ");
    }

    public static void writeMetricsOverviewGraph(Path graphFile, ReadmeMetricsHistory history) {
        writeMetricsOverviewGraph(graphFile, history, Instant.now());
    }

    public static void writeMetricsOverviewGraph(Path graphFile, ReadmeMetricsHistory history, Instant generatedAt) {
        writeTextWithTrailingNewline(
                graphFile,
                ReadmeMetricsGraphSupport.buildMetricsOverviewGraph(history, generatedAt, ReadmeMetricsGraphSupport.LIGHT_THEME),
                "Failed to write README metrics graph to "
        );
        writeTextWithTrailingNewline(
                ReadmeMetricsGraphSupport.darkMetricsOverviewGraphFile(graphFile),
                ReadmeMetricsGraphSupport.buildMetricsOverviewGraph(history, generatedAt, ReadmeMetricsGraphSupport.DARK_THEME),
                "Failed to write README metrics graph to "
        );
    }

    public static void writeCoverageMarkdown(
            Path coverageFile,
            Path statsRoot,
            Path metadataRoot,
            ReadmeBadgeSummary summary
    ) {
        writeTextWithTrailingNewline(
                coverageFile,
                buildCoverageMarkdown(statsRoot, metadataRoot, summary),
                "Failed to write coverage Markdown to "
        );
    }

    public static String buildCoverageMarkdown(
            Path statsRoot,
            Path metadataRoot,
            ReadmeBadgeSummary summary
    ) {
        return CoverageMarkdownSupport.buildCoverageMarkdown(statsRoot, metadataRoot, summary);
    }

    static String collapsibleDescriptionCell(String description) {
        return CoverageMarkdownSupport.collapsibleDescriptionCell(description);
    }

    private static StatsMetrics buildStatsMetrics(LibraryStatsModels.LibraryStats libraryStats) {
        int coverageStatsArtifacts = libraryStats.entries() == null ? 0 : libraryStats.entries().size();

        long dynamicAccessCoveredCalls = 0;
        long dynamicAccessTotalCalls = 0;
        int testedLinesOfCode = 0;
        if (libraryStats.entries() != null) {
            for (LibraryStatsModels.ArtifactStats artifactStats : libraryStats.entries().values()) {
                if (artifactStats == null || artifactStats.metadataVersions() == null) {
                    continue;
                }
                for (LibraryStatsModels.MetadataVersionStats metadataVersionStats : artifactStats.metadataVersions().values()) {
                    if (metadataVersionStats == null || metadataVersionStats.versions() == null) {
                        continue;
                    }
                    for (LibraryStatsModels.VersionStats versionStats : metadataVersionStats.versions()) {
                        if (versionStats == null) {
                            continue;
                        }
                        if (versionStats.dynamicAccess() != null && versionStats.dynamicAccess().isAvailable()) {
                            long totalCalls = versionStats.dynamicAccess().totalCalls();
                            if (totalCalls > 0) {
                                dynamicAccessCoveredCalls += versionStats.dynamicAccess().coveredCalls();
                                dynamicAccessTotalCalls += totalCalls;
                            }
                        }
                        if (versionStats.libraryCoverage() != null
                                && versionStats.libraryCoverage().line() != null
                                && versionStats.libraryCoverage().line().isAvailable()) {
                            testedLinesOfCode += Math.toIntExact(versionStats.libraryCoverage().line().covered());
                        }
                    }
                }
            }
        }

        BigDecimal dynamicAccessCallCoveragePercent = BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        if (dynamicAccessTotalCalls > 0) {
            dynamicAccessCallCoveragePercent = BigDecimal.valueOf(dynamicAccessCoveredCalls)
                    .divide(BigDecimal.valueOf(dynamicAccessTotalCalls), AVERAGE_SCALE, RoundingMode.HALF_UP)
                    .multiply(HUNDRED)
                    .setScale(1, RoundingMode.HALF_UP);
        }

        return new StatsMetrics(dynamicAccessCallCoveragePercent, coverageStatsArtifacts, testedLinesOfCode);
    }

    private static MetadataIndexMetrics buildMetadataIndexMetrics(Path metadataRoot) {
        int latestEntries = 0;
        int metadataBaselines = 0;
        int metadataIndexes = 0;
        int testedVersions = 0;

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
                List<MetadataVersionsIndexEntry> entries = readIndexEntries(indexFile);
                if (!hasMetadataIndexEntries(entries)) {
                    continue;
                }
                metadataIndexes++;
                for (MetadataVersionsIndexEntry entry : entries) {
                    if (!isMetadataIndexEntry(entry)) {
                        continue;
                    }
                    metadataBaselines++;
                    if (Boolean.TRUE.equals(entry.latest())) {
                        latestEntries++;
                    }
                    if (entry.testedVersions() != null) {
                        testedVersions += entry.testedVersions().size();
                    }
                }
            }
        } catch (IOException e) {
            throw new GradleException("Failed to traverse metadata root " + metadataRoot, e);
        }

        return new MetadataIndexMetrics(
                latestEntries,
                metadataBaselines,
                metadataIndexes,
                testedVersions
        );
    }

    static List<MetadataVersionsIndexEntry> readIndexEntries(Path indexFile) {
        try {
            return OBJECT_MAPPER.readValue(indexFile.toFile(), INDEX_ENTRIES_TYPE);
        } catch (IOException e) {
            throw new GradleException("Failed to read metadata index file " + indexFile, e);
        }
    }

    static boolean hasMetadataIndexEntries(List<MetadataVersionsIndexEntry> entries) {
        if (entries == null) {
            return false;
        }
        return entries.stream().anyMatch(ReadmeBadgeSummarySupport::isMetadataIndexEntry);
    }

    static boolean isMetadataIndexEntry(MetadataVersionsIndexEntry entry) {
        return entry != null && !entry.isNotForNativeImage() && entry.metadataVersion() != null;
    }

    static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void writeJsonWithTrailingNewline(Path targetFile, Object value, String errorPrefix) {
        try {
            Path parent = targetFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(targetFile.toFile(), value);
            String content = Files.readString(targetFile, StandardCharsets.UTF_8);
            if (!content.endsWith(System.lineSeparator())) {
                Files.writeString(targetFile, content + System.lineSeparator(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new GradleException(errorPrefix + targetFile, e);
        }
    }

    private static void writeTextWithTrailingNewline(Path targetFile, String value, String errorPrefix) {
        try {
            Path parent = targetFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String content = value.endsWith(System.lineSeparator()) ? value : value + System.lineSeparator();
            Files.writeString(targetFile, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new GradleException(errorPrefix + targetFile, e);
        }
    }

    public record ReadmeBadgeSummary(
            String date,
            BadgeValues badges,
            Metrics metrics
    ) {
    }

    public record ReadmeMetricsHistory(
            List<HistoryEntry> history
    ) {
    }

    public record HistoryEntry(
            String date,
            HistoryMetrics metrics
    ) {
    }

    public record HistoryMetrics(
            MetadataIndexMetrics metadataIndexes,
            StatsMetrics stats
    ) {
    }

    public record BadgeValues(
            String librariesSupported,
            String dynamicAccessCoverage,
            String testedLibraryVersions,
            String testedLinesOfCode
    ) {
    }

    public record Metrics(
            MetadataIndexMetrics metadataIndexes,
            StatsMetrics stats
    ) {
    }

    public record MetadataIndexMetrics(
            int latestEntries,
            int metadataBaselines,
            int metadataIndexes,
            int testedVersions
    ) {
    }

    public record StatsMetrics(
            BigDecimal dynamicAccessCallCoveragePercent,
            int coverageStatsArtifacts,
            int testedLinesOfCode
    ) {
    }
}
