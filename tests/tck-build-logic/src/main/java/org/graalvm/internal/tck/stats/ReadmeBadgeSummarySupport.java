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
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Builds the generated JSON payload used by README shields.io badges.
 */
public final class ReadmeBadgeSummarySupport {

    private static final int DASHBOARD_WIDTH = 1240;
    private static final int DASHBOARD_HEIGHT = 860;
    private static final int DASHBOARD_PADDING = 28;
    private static final int DASHBOARD_HEADER_HEIGHT = 92;
    private static final int DASHBOARD_GAP = 20;
    private static final int PANEL_WIDTH = (DASHBOARD_WIDTH - (DASHBOARD_PADDING * 2) - DASHBOARD_GAP) / 2;
    private static final int PANEL_HEIGHT = (DASHBOARD_HEIGHT - DASHBOARD_HEADER_HEIGHT - (DASHBOARD_PADDING * 2) - DASHBOARD_GAP) / 2;
    private static final int PANEL_PLOT_TOP = 86;
    private static final int PANEL_PLOT_RIGHT = 34;
    private static final int PANEL_PLOT_BOTTOM = 64;
    private static final int PANEL_PLOT_LEFT = 62;
    private static final int PANEL_X_LABEL_INSET = 12;
    private static final int PANEL_FRAME_BOTTOM_GAP = 18;
    private static final BigDecimal PERCENT_MIN_RANGE = new BigDecimal("10.0");
    private static final BigDecimal PERCENT_PADDING = new BigDecimal("2.0");
    private static final BigDecimal PERCENT_TICK_STEP = new BigDecimal("5.0");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private static final TypeReference<List<MetadataVersionsIndexEntry>> INDEX_ENTRIES_TYPE = new TypeReference<>() {
    };

    private static final Set<String> EXCLUDED_GROUP_IDS = Set.of("org.example", "samples");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int AVERAGE_SCALE = 6;

    private ReadmeBadgeSummarySupport() {
    }

    public static ReadmeBadgeSummary buildSummary(
            Path statsFile,
            Path metadataRoot
    ) {
        return buildSummary(statsFile, metadataRoot, LocalDate.now(ZoneOffset.UTC));
    }

    public static ReadmeBadgeSummary buildSummary(
            Path statsFile,
            Path metadataRoot,
            LocalDate snapshotDate
    ) {
        requireRegularFile(statsFile, "Missing stats file: ");
        if (!Files.isDirectory(metadataRoot)) {
            throw new GradleException("Missing metadata root: " + metadataRoot);
        }

        LibraryStatsModels.LibraryStats libraryStats = LibraryStatsSupport.loadStats(statsFile);

        StatsMetrics statsMetrics = buildStatsMetrics(libraryStats);
        MetadataIndexMetrics metadataIndexMetrics = buildMetadataIndexMetrics(metadataRoot);

        return new ReadmeBadgeSummary(
                snapshotDate.toString(),
                new BadgeValues(
                        formatInteger(metadataIndexMetrics.metadataIndexes()),
                        formatPercent(statsMetrics.avgDynamicAccessCoveragePercent()),
                        formatInteger(metadataIndexMetrics.testedVersions()),
                        formatInteger(statsMetrics.testedLinesOfCode())
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
        writeTextWithTrailingNewline(
                graphFile,
                buildMetricsOverviewGraph(history),
                "Failed to write README metrics graph to "
        );
    }

    private static StatsMetrics buildStatsMetrics(LibraryStatsModels.LibraryStats libraryStats) {
        int coverageStatsArtifacts = libraryStats.entries() == null ? 0 : libraryStats.entries().size();

        BigDecimal dynamicAccessRatioSum = BigDecimal.ZERO;
        long dynamicAccessRatioCount = 0;
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
                            dynamicAccessRatioSum = dynamicAccessRatioSum.add(versionStats.dynamicAccess().coverageRatio());
                            dynamicAccessRatioCount++;
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

        BigDecimal avgDynamicAccessCoveragePercent = BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        if (dynamicAccessRatioCount > 0) {
            avgDynamicAccessCoveragePercent = dynamicAccessRatioSum
                    .divide(BigDecimal.valueOf(dynamicAccessRatioCount), AVERAGE_SCALE, RoundingMode.HALF_UP)
                    .multiply(HUNDRED)
                    .setScale(1, RoundingMode.HALF_UP);
        }

        return new StatsMetrics(avgDynamicAccessCoveragePercent, coverageStatsArtifacts, testedLinesOfCode);
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
                metadataIndexes++;
                metadataBaselines += entries.size();
                for (MetadataVersionsIndexEntry entry : entries) {
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

    private static List<MetadataVersionsIndexEntry> readIndexEntries(Path indexFile) {
        try {
            return OBJECT_MAPPER.readValue(indexFile.toFile(), INDEX_ENTRIES_TYPE);
        } catch (IOException e) {
            throw new GradleException("Failed to read metadata index file " + indexFile, e);
        }
    }

    private static String buildMetricsOverviewGraph(ReadmeMetricsHistory history) {
        List<PanelSpec> panels = List.of(
                new PanelSpec(
                        "libraries-supported",
                        "Supported libraries",
                        "Libraries with reachability metadata in the repository",
                        false,
                        "count",
                        "#0a7ec2",
                        "#38bdf8",
                        extractMetricPoints(history, entry -> BigDecimal.valueOf(entry.metrics().metadataIndexes().metadataIndexes()))
                ),
                new PanelSpec(
                        "tested-versions",
                        "Tested library versions",
                        "Tested library versions recorded across metadata indexes",
                        false,
                        "count",
                        "#bf8700",
                        "#f59e0b",
                        extractMetricPoints(history, entry -> BigDecimal.valueOf(entry.metrics().metadataIndexes().testedVersions()))
                ),
                new PanelSpec(
                        "dynamic-access",
                        "Dynamic access coverage",
                        "Average dynamic-access call coverage across repository metadata",
                        true,
                        "percent",
                        "#1f9d55",
                        "#34d399",
                        extractMetricPoints(history, entry -> entry.metrics().stats().avgDynamicAccessCoveragePercent())
                ),
                new PanelSpec(
                        "tested-lines-of-code",
                        "Tested lines of code",
                        "Covered lines reported across library coverage stats",
                        false,
                        "count",
                        "#c2410c",
                        "#fb923c",
                        extractMetricPoints(history, entry -> BigDecimal.valueOf(entry.metrics().stats().testedLinesOfCode()))
                )
        );

        LocalDate latestDate = panels.stream()
                .flatMap(panel -> panel.points().stream())
                .map(MetricPoint::date)
                .max(LocalDate::compareTo)
                .orElse(LocalDate.now(ZoneOffset.UTC));

        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
                .append(DASHBOARD_WIDTH)
                .append(' ')
                .append(DASHBOARD_HEIGHT)
                .append("\" role=\"img\" aria-labelledby=\"graph-title graph-desc\">")
                .append('\n');
        svg.append("  <title id=\"graph-title\">Coverage over time</title>\n");
        svg.append("  <desc id=\"graph-desc\">A four-panel coverage dashboard showing libraries supported, tested library versions, dynamic access coverage, and tested lines of code over time.</desc>\n");
        svg.append("  <defs>\n");
        svg.append("    <linearGradient id=\"dashboard-bg\" x1=\"0\" y1=\"0\" x2=\"1\" y2=\"1\">\n");
        svg.append("      <stop offset=\"0%\" stop-color=\"#f7fbff\"/>\n");
        svg.append("      <stop offset=\"100%\" stop-color=\"#fff8ee\"/>\n");
        svg.append("    </linearGradient>\n");
        svg.append("    <filter id=\"panel-shadow\" x=\"-10%\" y=\"-10%\" width=\"120%\" height=\"120%\">\n");
        svg.append("      <feDropShadow dx=\"0\" dy=\"12\" stdDeviation=\"18\" flood-color=\"#0f172a\" flood-opacity=\"0.09\"/>\n");
        svg.append("    </filter>\n");
        for (PanelSpec panel : panels) {
            svg.append("    <linearGradient id=\"")
                    .append(panel.id())
                    .append("-area\" x1=\"0\" y1=\"0\" x2=\"0\" y2=\"1\">\n");
            svg.append("      <stop offset=\"0%\" stop-color=\"")
                    .append(panel.highlightColor())
                    .append("\" stop-opacity=\"0.30\"/>\n");
            svg.append("      <stop offset=\"100%\" stop-color=\"")
                    .append(panel.highlightColor())
                    .append("\" stop-opacity=\"0.04\"/>\n");
            svg.append("    </linearGradient>\n");
        }
        svg.append("  </defs>\n");
        svg.append("  <rect width=\"100%\" height=\"100%\" rx=\"30\" fill=\"url(#dashboard-bg)\"/>\n");
        svg.append("  <text x=\"")
                .append(DASHBOARD_PADDING)
                .append("\" y=\"62\" fill=\"#0f172a\" font-size=\"34\" font-weight=\"700\">Coverage over time</text>\n");
        svg.append("  <text x=\"")
                .append(DASHBOARD_PADDING)
                .append("\" y=\"92\" fill=\"#5f748c\" font-size=\"17\">Updated ")
                .append(escapeXml(latestDate.toString()))
                .append("</text>\n");
        for (int index = 0; index < panels.size(); index++) {
            int panelX = DASHBOARD_PADDING + (index % 2) * (PANEL_WIDTH + DASHBOARD_GAP);
            int panelY = DASHBOARD_HEADER_HEIGHT + DASHBOARD_PADDING + (index / 2) * (PANEL_HEIGHT + DASHBOARD_GAP);
            appendPanel(svg, panels.get(index), panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT);
        }

        svg.append("</svg>");
        return svg.toString();
    }

    private static List<MetricPoint> extractMetricPoints(
            ReadmeMetricsHistory history,
            Function<HistoryEntry, BigDecimal> valueExtractor
    ) {
        List<MetricPoint> points = new ArrayList<>();
        if (history == null || history.history() == null) {
            return points;
        }
        for (HistoryEntry entry : history.history()) {
            if (entry == null || entry.date() == null || entry.metrics() == null) {
                continue;
            }
            BigDecimal value = valueExtractor.apply(entry);
            if (value == null) {
                continue;
            }
            points.add(new MetricPoint(LocalDate.parse(entry.date()), value));
        }
        return points;
    }

    private static void appendPanel(StringBuilder svg, PanelSpec panel, int panelX, int panelY, int panelWidth, int panelHeight) {
        int plotX = panelX + PANEL_PLOT_LEFT;
        int plotY = panelY + PANEL_PLOT_TOP;
        int plotWidth = panelWidth - PANEL_PLOT_LEFT - PANEL_PLOT_RIGHT;
        int plotHeight = panelHeight - PANEL_PLOT_TOP - PANEL_PLOT_BOTTOM;
        int plotBottom = plotY + plotHeight;
        int frameBottom = panelY + panelHeight - PANEL_FRAME_BOTTOM_GAP;
        int frameHeight = frameBottom - plotY;

        svg.append("  <g>\n");
        svg.append("    <rect x=\"")
                .append(panelX)
                .append("\" y=\"")
                .append(panelY)
                .append("\" width=\"")
                .append(panelWidth)
                .append("\" height=\"")
                .append(panelHeight)
                .append("\" rx=\"24\" fill=\"#ffffff\" fill-opacity=\"0.90\" stroke=\"#dbe7f3\" filter=\"url(#panel-shadow)\"/>\n");
        svg.append("    <text x=\"")
                .append(panelX + 24)
                .append("\" y=\"")
                .append(panelY + 34)
                .append("\" fill=\"#0f172a\" font-size=\"23\" font-weight=\"700\">")
                .append(escapeXml(panel.title()))
                .append("</text>\n");
        svg.append("    <text x=\"")
                .append(panelX + 24)
                .append("\" y=\"")
                .append(panelY + 56)
                .append("\" fill=\"#667b92\" font-size=\"15\">")
                .append(escapeXml(panel.subtitle()))
                .append("</text>\n");

        if (panel.points().isEmpty()) {
            svg.append("    <text x=\"")
                    .append(panelX + (panelWidth / 2))
                    .append("\" y=\"")
                    .append(panelY + (panelHeight / 2))
                    .append("\" fill=\"#6b7f95\" font-size=\"17\" text-anchor=\"middle\">No history entries yet</text>\n");
            svg.append("  </g>\n");
            return;
        }

        MetricPoint latestPoint = panel.points().get(panel.points().size() - 1);
        ChartBounds bounds = calculateBounds(panel.points(), panel.percentMetric());
        List<BigDecimal> yTicks = buildYAxisTicks(bounds.minValue(), bounds.maxValue());
        List<Integer> xLabelIndexes = buildXLabelIndexes(panel.points().size());

        for (BigDecimal tick : yTicks) {
            double y = toPlotY(tick, bounds.minValue(), bounds.maxValue(), plotY, plotHeight);
            svg.append("    <line x1=\"")
                    .append(plotX)
                    .append("\" y1=\"")
                    .append(formatDecimal(y))
                    .append("\" x2=\"")
                    .append(plotX + plotWidth)
                    .append("\" y2=\"")
                    .append(formatDecimal(y))
                    .append("\" stroke=\"#eef3f8\" stroke-width=\"1\"/>\n");
            svg.append("    <text x=\"")
                    .append(plotX - 10)
                    .append("\" y=\"")
                    .append(formatDecimal(y + 4))
                    .append("\" fill=\"#71859a\" font-size=\"13\" text-anchor=\"end\">")
                    .append(escapeXml(formatTickValue(tick, panel.valueFormat())))
                    .append("</text>\n");
        }

        for (Integer pointIndex : xLabelIndexes) {
            MetricPoint point = panel.points().get(pointIndex);
            double x = toPlotX(pointIndex, panel.points().size(), plotX, plotWidth);
            boolean firstLabel = pointIndex == 0;
            boolean lastLabel = pointIndex == panel.points().size() - 1;
            String textAnchor = firstLabel ? "start" : lastLabel ? "end" : "middle";
            double labelX = firstLabel
                    ? x + PANEL_X_LABEL_INSET
                    : lastLabel
                    ? x - PANEL_X_LABEL_INSET
                    : x;
            svg.append("    <line x1=\"")
                    .append(formatDecimal(x))
                    .append("\" y1=\"")
                    .append(plotY)
                    .append("\" x2=\"")
                    .append(formatDecimal(x))
                    .append("\" y2=\"")
                    .append(plotBottom)
                    .append("\" stroke=\"#f6f8fb\" stroke-width=\"1\"/>\n");
            svg.append("    <text x=\"")
                    .append(formatDecimal(labelX))
                    .append("\" y=\"")
                    .append(panelY + panelHeight - 24)
                    .append("\" fill=\"#6b7f95\" font-size=\"13\" text-anchor=\"")
                    .append(textAnchor)
                    .append("\">")
                    .append(escapeXml(formatGraphDate(point.date())))
                    .append("</text>\n");
        }

        svg.append("    <rect x=\"")
                .append(plotX)
                .append("\" y=\"")
                .append(plotY)
                .append("\" width=\"")
                .append(plotWidth)
                .append("\" height=\"")
                .append(frameHeight)
                .append("\" rx=\"18\" fill=\"none\" stroke=\"#edf3f8\"/>\n");

        String areaPath = buildAreaPath(panel.points(), bounds.minValue(), bounds.maxValue(), plotX, plotY, plotWidth, plotHeight, plotBottom);
        String linePath = buildLinePath(panel.points(), bounds.minValue(), bounds.maxValue(), plotX, plotY, plotWidth, plotHeight);
        svg.append("    <path d=\"")
                .append(areaPath)
                .append("\" fill=\"url(#")
                .append(panel.id())
                .append("-area)\"/>\n");
        svg.append("    <path d=\"")
                .append(linePath)
                .append("\" fill=\"none\" stroke=\"")
                .append(panel.primaryColor())
                .append("\" stroke-width=\"3.5\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>\n");

        double latestX = toPlotX(panel.points().size() - 1, panel.points().size(), plotX, plotWidth);
        double latestY = toPlotY(latestPoint.value(), bounds.minValue(), bounds.maxValue(), plotY, plotHeight);
        svg.append("    <circle cx=\"")
                .append(formatDecimal(latestX))
                .append("\" cy=\"")
                .append(formatDecimal(latestY))
                .append("\" r=\"6\" fill=\"#ffffff\" stroke=\"")
                .append(panel.primaryColor())
                .append("\" stroke-width=\"3.5\"/>\n");

        String latestValue = formatMetricValue(latestPoint.value(), panel.valueFormat());
        double badgeWidth = Math.max(78.0d, Math.min(140.0d, 26.0d + (latestValue.length() * 7.2d)));
        double badgeX = Math.max(panelX + 24.0d, Math.min(panelX + panelWidth - badgeWidth - 24.0d, latestX - (badgeWidth / 2.0d)));
        double badgeY = Math.max(panelY + 22.0d, latestY - 40.0d);
        svg.append("    <g transform=\"translate(")
                .append(formatDecimal(badgeX))
                .append(' ')
                .append(formatDecimal(badgeY))
                .append(")\">\n");
        svg.append("      <rect width=\"")
                .append(formatDecimal(badgeWidth))
                .append("\" height=\"28\" rx=\"14\" fill=\"#0f172a\"/>\n");
        svg.append("      <text x=\"")
                .append(formatDecimal(badgeWidth / 2.0d))
                .append("\" y=\"19\" fill=\"#ffffff\" font-size=\"15\" font-weight=\"700\" text-anchor=\"middle\">")
                .append(escapeXml(latestValue))
                .append("</text>\n");
        svg.append("    </g>\n");
        svg.append("  </g>\n");
    }

    private static ChartBounds calculateBounds(List<MetricPoint> points, boolean percentMetric) {
        BigDecimal minValue = points.stream().map(MetricPoint::value).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal maxValue = points.stream().map(MetricPoint::value).max(BigDecimal::compareTo).orElse(BigDecimal.ONE);
        if (percentMetric) {
            BigDecimal boundedMin = roundDown(minValue.subtract(PERCENT_PADDING), PERCENT_TICK_STEP).max(BigDecimal.ZERO);
            BigDecimal boundedMax = roundUp(maxValue.add(PERCENT_PADDING), PERCENT_TICK_STEP).min(HUNDRED);
            BigDecimal range = boundedMax.subtract(boundedMin);
            if (range.compareTo(PERCENT_MIN_RANGE) < 0) {
                BigDecimal deficit = PERCENT_MIN_RANGE.subtract(range);
                boundedMin = boundedMin.subtract(deficit.divide(BigDecimal.valueOf(2), 1, RoundingMode.HALF_UP)).max(BigDecimal.ZERO);
                boundedMax = boundedMin.add(PERCENT_MIN_RANGE).min(HUNDRED);
                boundedMin = boundedMax.subtract(PERCENT_MIN_RANGE).max(BigDecimal.ZERO);
            }
            return new ChartBounds(boundedMin, boundedMax);
        }

        BigDecimal range = maxValue.subtract(minValue);
        BigDecimal padding = range.compareTo(BigDecimal.TEN) < 0
                ? BigDecimal.valueOf(2)
                : range.multiply(new BigDecimal("0.10")).setScale(0, RoundingMode.CEILING);
        BigDecimal boundedMin = minValue.subtract(padding).max(BigDecimal.ZERO).setScale(0, RoundingMode.FLOOR);
        BigDecimal boundedMax = maxValue.add(padding).setScale(0, RoundingMode.CEILING);
        if (boundedMax.subtract(boundedMin).compareTo(BigDecimal.valueOf(8)) < 0) {
            boundedMax = boundedMin.add(BigDecimal.valueOf(8));
        }
        return new ChartBounds(boundedMin, boundedMax);
    }

    private static List<BigDecimal> buildYAxisTicks(BigDecimal minValue, BigDecimal maxValue) {
        List<BigDecimal> ticks = new ArrayList<>();
        BigDecimal step = maxValue.subtract(minValue).divide(BigDecimal.valueOf(4), 1, RoundingMode.HALF_UP);
        for (int index = 0; index < 5; index++) {
            BigDecimal tick = minValue.add(step.multiply(BigDecimal.valueOf(index))).setScale(1, RoundingMode.HALF_UP);
            ticks.add(tick);
        }
        return ticks;
    }

    private static List<Integer> buildXLabelIndexes(int pointCount) {
        List<Integer> indexes = new ArrayList<>();
        if (pointCount <= 0) {
            return indexes;
        }
        TreeSet<Integer> unique = new TreeSet<>();
        unique.add(0);
        unique.add(pointCount - 1);
        if (pointCount > 2) {
            unique.add((pointCount - 1) / 2);
        }
        if (pointCount > 20) {
            unique.add((pointCount - 1) / 3);
            unique.add(((pointCount - 1) * 2) / 3);
        }
        indexes.addAll(unique);
        return indexes;
    }

    private static String buildLinePath(
            List<MetricPoint> points,
            BigDecimal minValue,
            BigDecimal maxValue,
            int plotX,
            int plotY,
            int plotWidth,
            int plotHeight
    ) {
        StringBuilder path = new StringBuilder();
        for (int index = 0; index < points.size(); index++) {
            double x = toPlotX(index, points.size(), plotX, plotWidth);
            double y = toPlotY(points.get(index).value(), minValue, maxValue, plotY, plotHeight);
            path.append(index == 0 ? "M " : " L ")
                    .append(formatDecimal(x))
                    .append(' ')
                    .append(formatDecimal(y));
        }
        return path.toString();
    }

    private static String buildAreaPath(
            List<MetricPoint> points,
            BigDecimal minValue,
            BigDecimal maxValue,
            int plotX,
            int plotY,
            int plotWidth,
            int plotHeight,
            int plotBottom
    ) {
        if (points.isEmpty()) {
            return "";
        }
        StringBuilder path = new StringBuilder(buildLinePath(points, minValue, maxValue, plotX, plotY, plotWidth, plotHeight));
        double lastX = toPlotX(points.size() - 1, points.size(), plotX, plotWidth);
        double firstX = toPlotX(0, points.size(), plotX, plotWidth);
        path.append(" L ")
                .append(formatDecimal(lastX))
                .append(' ')
                .append(plotBottom)
                .append(" L ")
                .append(formatDecimal(firstX))
                .append(' ')
                .append(plotBottom)
                .append(" Z");
        return path.toString();
    }

    private static double toPlotX(int index, int pointCount, int plotX, int plotWidth) {
        if (pointCount <= 1) {
            return plotX + (plotWidth / 2.0d);
        }
        return plotX + ((double) index / (pointCount - 1)) * plotWidth;
    }

    private static double toPlotY(BigDecimal value, BigDecimal minValue, BigDecimal maxValue, int plotY, int plotHeight) {
        BigDecimal range = maxValue.subtract(minValue);
        if (range.compareTo(BigDecimal.ZERO) <= 0) {
            return plotY + (plotHeight / 2.0d);
        }
        BigDecimal normalized = value.subtract(minValue)
                .divide(range, 6, RoundingMode.HALF_UP);
        return plotY + plotHeight - (normalized.doubleValue() * plotHeight);
    }

    private static BigDecimal roundDown(BigDecimal value, BigDecimal step) {
        return value.divide(step, 0, RoundingMode.FLOOR).multiply(step).setScale(1, RoundingMode.HALF_UP);
    }

    private static BigDecimal roundUp(BigDecimal value, BigDecimal step) {
        return value.divide(step, 0, RoundingMode.CEILING).multiply(step).setScale(1, RoundingMode.HALF_UP);
    }

    private static String formatGraphDate(LocalDate date) {
        return date.getMonth().name().substring(0, 1)
                + date.getMonth().name().substring(1, 3).toLowerCase(Locale.ROOT)
                + " "
                + date.getYear();
    }

    private static String formatTickValue(BigDecimal value, String valueFormat) {
        if ("percent".equals(valueFormat)) {
            return formatPercent(value);
        }
        return formatInteger(value.setScale(0, RoundingMode.HALF_UP).intValue());
    }

    private static String formatMetricValue(BigDecimal value, String valueFormat) {
        if ("percent".equals(valueFormat)) {
            return formatPercent(value);
        }
        return formatInteger(value.setScale(0, RoundingMode.HALF_UP).intValue());
    }

    private static String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String formatDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
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

    private static void requireRegularFile(Path file, String errorPrefix) {
        if (!Files.isRegularFile(file)) {
            throw new GradleException(errorPrefix + file);
        }
    }

    private static String formatInteger(int value) {
        NumberFormat format = NumberFormat.getIntegerInstance(Locale.US);
        format.setGroupingUsed(true);
        return format.format(value);
    }

    private static String formatPercent(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP).toPlainString() + "%";
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
            BigDecimal avgDynamicAccessCoveragePercent,
            int coverageStatsArtifacts,
            int testedLinesOfCode
    ) {
    }

    private record MetricPoint(
            LocalDate date,
            BigDecimal value
    ) {
    }

    private record PanelSpec(
            String id,
            String title,
            String subtitle,
            boolean percentMetric,
            String valueFormat,
            String primaryColor,
            String highlightColor,
            List<MetricPoint> points
    ) {
    }

    private record ChartBounds(
            BigDecimal minValue,
            BigDecimal maxValue
    ) {
    }
}
