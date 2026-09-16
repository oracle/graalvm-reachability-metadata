/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.stats;

import org.gradle.api.GradleException;
import org.gradle.util.internal.VersionNumber;
import org.graalvm.internal.tck.model.MetadataVersionsIndexEntry;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Builds the per-library coverage Markdown table from metadata and stats.
 */
final class CoverageMarkdownSupport {

    private CoverageMarkdownSupport() {
    }

    static String buildCoverageMarkdown(
            Path statsRoot,
            Path metadataRoot,
            ReadmeBadgeSummarySupport.ReadmeBadgeSummary summary
    ) {
        List<CoverageTableEntry> entries = buildCoverageTableEntries(statsRoot, metadataRoot);
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Coverage\n\n");
        markdown.append("Updated: ")
                .append(summary.date())
                .append("\n\n");
        markdown.append("![Coverage over Time](https://raw.githubusercontent.com/oracle/graalvm-reachability-metadata/")
                .append("stats/coverage/latest/metrics-over-time.svg#gh-light-mode-only)\n");
        markdown.append("![Coverage over Time](https://raw.githubusercontent.com/oracle/graalvm-reachability-metadata/")
                .append("stats/coverage/latest/metrics-over-time-dark.svg#gh-dark-mode-only)\n\n");
        markdown.append("## Libraries\n\n");
        markdown.append("| Library | Versions | Description | Dynamic access coverage |\n");
        markdown.append("| --- | --- | --- | ---: |\n");
        for (CoverageTableEntry entry : entries) {
            markdown.append("| `")
                    .append(markdownCell(entry.coordinate()))
                    .append("` | ")
                    .append(markdownLink(entry.versionSummary(), entry.metadataIndexPath()))
                    .append(" | ")
                    .append(collapsibleDescriptionCell(entry.description()))
                    .append(" | ")
                    .append(markdownLinkOrText(
                            formatDynamicAccessCoverage(entry.dynamicAccessCoverage()),
                            entry.statsPath()
                    ))
                    .append(" |\n");
        }
        return markdown.toString();
    }

    static String collapsibleDescriptionCell(String description) {
        if (description == null) {
            return "";
        }
        String normalized = description.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            return "";
        }
        return "<details><summary>Show</summary> " + escapeMarkdownTableHtml(normalized) + "</details>";
    }

    private static String escapeMarkdownTableHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("|", "\\|");
    }

    private static List<CoverageTableEntry> buildCoverageTableEntries(Path statsRoot, Path metadataRoot) {
        if (!Files.isDirectory(metadataRoot)) {
            throw new GradleException("Missing metadata root: " + metadataRoot);
        }
        LibraryStatsModels.LibraryStats libraryStats = LibraryStatsSupport.loadRepositoryStats(statsRoot);
        Map<String, CoverageTableEntry> entriesByCoordinate = new TreeMap<>();

        try (Stream<Path> paths = Files.find(metadataRoot, 3, (path, attrs) -> {
            if (!attrs.isRegularFile()) {
                return false;
            }
            Path relative = metadataRoot.relativize(path);
            if (relative.getNameCount() != 3) {
                return false;
            }
            String groupId = relative.getName(0).toString();
            return "index.json".equals(relative.getName(2).toString())
                    && !ReadmeBadgeSummarySupport.EXCLUDED_GROUP_IDS.contains(groupId);
        })) {
            for (Path indexFile : paths.sorted().toList()) {
                Path relative = metadataRoot.relativize(indexFile);
                String groupId = relative.getName(0).toString();
                String artifactId = relative.getName(1).toString();
                String coordinate = groupId + ":" + artifactId;
                List<MetadataVersionsIndexEntry> indexEntries = ReadmeBadgeSummarySupport.readIndexEntries(indexFile);
                if (!ReadmeBadgeSummarySupport.hasMetadataIndexEntries(indexEntries)) {
                    continue;
                }
                entriesByCoordinate.put(
                        coordinate,
                        new CoverageTableEntry(
                                coordinate,
                                formatVersionSummary(indexEntries),
                                "metadata/" + groupId + "/" + artifactId + "/index.json",
                                statsLinkTarget(statsRoot, groupId, artifactId),
                                selectDescription(indexEntries),
                                aggregateDynamicAccessCoverage(libraryStats.entries().get(coordinate))
                        )
                );
            }
        } catch (IOException e) {
            throw new GradleException("Failed to traverse metadata root " + metadataRoot, e);
        }

        return List.copyOf(entriesByCoordinate.values());
    }

    private static String statsLinkTarget(Path statsRoot, String groupId, String artifactId) {
        if (Files.isDirectory(statsRoot.resolve(groupId).resolve(artifactId))) {
            return "stats/" + groupId + "/" + artifactId + "/";
        }
        return "";
    }

    private static String selectDescription(List<MetadataVersionsIndexEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return "";
        }
        for (MetadataVersionsIndexEntry entry : entries) {
            if (entry != null && Boolean.TRUE.equals(entry.latest()) && !ReadmeBadgeSummarySupport.isBlank(entry.description())) {
                return entry.description();
            }
        }
        for (MetadataVersionsIndexEntry entry : entries) {
            if (entry != null && !ReadmeBadgeSummarySupport.isBlank(entry.description())) {
                return entry.description();
            }
        }
        return "";
    }

    private static String formatVersionSummary(List<MetadataVersionsIndexEntry> entries) {
        List<String> testedVersions = new ArrayList<>();
        for (MetadataVersionsIndexEntry entry : entries) {
            if (!ReadmeBadgeSummarySupport.isMetadataIndexEntry(entry)) {
                continue;
            }
            if (entry.testedVersions() != null && !entry.testedVersions().isEmpty()) {
                for (String testedVersion : entry.testedVersions()) {
                    addVersionIfAbsent(testedVersions, testedVersion);
                }
            } else if (!ReadmeBadgeSummarySupport.isBlank(entry.metadataVersion())) {
                addVersionIfAbsent(testedVersions, entry.metadataVersion());
            }
        }
        if (testedVersions.isEmpty()) {
            return "";
        }
        testedVersions.sort(Comparator.comparing(VersionNumber::parse)
                .thenComparing(Function.identity()));
        if (testedVersions.size() == 1) {
            return testedVersions.get(0);
        }
        return testedVersions.get(0) + "<br>- " + testedVersions.get(testedVersions.size() - 1);
    }

    private static void addVersionIfAbsent(List<String> versions, String version) {
        if (!ReadmeBadgeSummarySupport.isBlank(version) && !versions.contains(version)) {
            versions.add(version);
        }
    }

    private static DynamicAccessCoverage aggregateDynamicAccessCoverage(LibraryStatsModels.ArtifactStats artifactStats) {
        if (artifactStats == null || artifactStats.metadataVersions() == null) {
            return DynamicAccessCoverage.notAvailable();
        }

        BigDecimal ratioSum = BigDecimal.ZERO;
        long ratioCount = 0;
        long coveredCalls = 0;
        long totalCalls = 0;
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
            }
        }

        if (ratioCount == 0) {
            return DynamicAccessCoverage.notAvailable();
        }

        BigDecimal coveragePercent;
        if (totalCalls > 0) {
            coveragePercent = BigDecimal.valueOf(coveredCalls)
                    .divide(BigDecimal.valueOf(totalCalls), ReadmeBadgeSummarySupport.AVERAGE_SCALE, RoundingMode.HALF_UP)
                    .multiply(ReadmeBadgeSummarySupport.HUNDRED)
                    .setScale(1, RoundingMode.HALF_UP);
        } else {
            coveragePercent = ratioSum
                    .divide(BigDecimal.valueOf(ratioCount), ReadmeBadgeSummarySupport.AVERAGE_SCALE, RoundingMode.HALF_UP)
                    .multiply(ReadmeBadgeSummarySupport.HUNDRED)
                    .setScale(1, RoundingMode.HALF_UP);
        }
        return new DynamicAccessCoverage(coveragePercent, coveredCalls, totalCalls, true);
    }

    private static String formatDynamicAccessCoverage(DynamicAccessCoverage coverage) {
        if (!coverage.available()) {
            return "N/A";
        }
        return MetricsChartSupport.formatPercent(coverage.coveragePercent())
                + " ("
                + MetricsChartSupport.formatInteger(coverage.coveredCalls())
                + "/"
                + MetricsChartSupport.formatInteger(coverage.totalCalls())
                + " calls)";
    }

    private static String markdownCell(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .replaceAll("\\s+", " ")
                .replace("|", "\\|");
    }

    private static String markdownLink(String label, String target) {
        if (ReadmeBadgeSummarySupport.isBlank(label)) {
            return "";
        }
        return "[" + markdownCell(label) + "](" + target + ")";
    }

    private static String markdownLinkOrText(String label, String target) {
        if (ReadmeBadgeSummarySupport.isBlank(target)) {
            return markdownCell(label);
        }
        return markdownLink(label, target);
    }

    private record CoverageTableEntry(
            String coordinate,
            String versionSummary,
            String metadataIndexPath,
            String statsPath,
            String description,
            DynamicAccessCoverage dynamicAccessCoverage
    ) {
    }

    private record DynamicAccessCoverage(
            BigDecimal coveragePercent,
            long coveredCalls,
            long totalCalls,
            boolean available
    ) {

        static DynamicAccessCoverage notAvailable() {
            return new DynamicAccessCoverage(BigDecimal.ZERO, 0, 0, false);
        }
    }
}
