/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.stats;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Renders the four-panel "Coverage over Time" SVG dashboard.
 */
final class ReadmeMetricsGraphSupport {

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

    static final ThemePalette LIGHT_THEME = new ThemePalette(
            "#f7fbff",
            "#fff8ee",
            "#0f172a",
            "0.09",
            "#0f172a",
            "#5f748c",
            "#ffffff",
            "0.90",
            "#dbe7f3",
            "#667b92",
            "#eef3f8",
            "#71859a",
            "#f6f8fb",
            "#6b7f95",
            "#edf3f8",
            "#ffffff",
            "#0f172a",
            "#ffffff"
    );
    static final ThemePalette DARK_THEME = new ThemePalette(
            "#0d1117",
            "#161b22",
            "#000000",
            "0.24",
            "#f0f6fc",
            "#8b949e",
            "#161b22",
            "0.96",
            "#30363d",
            "#8b949e",
            "#30363d",
            "#8b949e",
            "#21262d",
            "#8b949e",
            "#30363d",
            "#0d1117",
            "#f0f6fc",
            "#0d1117"
    );

    private static final DateTimeFormatter GENERATED_AT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'", Locale.ROOT).withZone(ZoneOffset.UTC);

    private ReadmeMetricsGraphSupport() {
    }

    static String buildMetricsOverviewGraph(
            ReadmeBadgeSummarySupport.ReadmeMetricsHistory history,
            Instant generatedAt,
            ThemePalette theme
    ) {
        List<PanelSpec> panels = List.of(
                new PanelSpec(
                        "libraries-supported",
                        "Supported Libraries",
                        "Libraries with reachability metadata in the repository",
                        false,
                        "count",
                        "#0a7ec2",
                        "#38bdf8",
                        extractMetricPoints(history, entry -> BigDecimal.valueOf(entry.metrics().metadataIndexes().metadataIndexes()))
                ),
                new PanelSpec(
                        "tested-versions",
                        "Tested Library Versions",
                        "Tested library versions recorded across metadata indexes",
                        false,
                        "count",
                        "#bf8700",
                        "#f59e0b",
                        extractMetricPoints(history, entry -> BigDecimal.valueOf(entry.metrics().metadataIndexes().testedVersions()))
                ),
                new PanelSpec(
                        "dynamic-access",
                        "Dynamic Access Coverage",
                        "Dynamic-access call coverage across repository metadata",
                        true,
                        "percent",
                        "#1f9d55",
                        "#34d399",
                        extractMetricPoints(history, entry -> entry.metrics().stats().dynamicAccessCallCoveragePercent())
                ),
                new PanelSpec(
                        "tested-lines-of-code",
                        "Tested Lines of Code",
                        "Covered lines reported across library coverage stats",
                        false,
                        "count",
                        "#c2410c",
                        "#fb923c",
                        extractMetricPoints(history, entry -> BigDecimal.valueOf(entry.metrics().stats().testedLinesOfCode()))
                )
        );

        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
                .append(DASHBOARD_WIDTH)
                .append(' ')
                .append(DASHBOARD_HEIGHT)
                .append("\" role=\"img\" aria-labelledby=\"graph-title graph-desc\">")
                .append('\n');
        svg.append("  <title id=\"graph-title\">Coverage over Time</title>\n");
        svg.append("  <desc id=\"graph-desc\">A four-panel coverage dashboard showing libraries supported, tested library versions, dynamic access coverage, and tested lines of code over time.</desc>\n");
        svg.append("  <defs>\n");
        svg.append("    <linearGradient id=\"dashboard-bg\" x1=\"0\" y1=\"0\" x2=\"1\" y2=\"1\">\n");
        svg.append("      <stop offset=\"0%\" stop-color=\"")
                .append(theme.dashboardStart())
                .append("\"/>\n");
        svg.append("      <stop offset=\"100%\" stop-color=\"")
                .append(theme.dashboardEnd())
                .append("\"/>\n");
        svg.append("    </linearGradient>\n");
        svg.append("    <filter id=\"panel-shadow\" x=\"-10%\" y=\"-10%\" width=\"120%\" height=\"120%\">\n");
        svg.append("      <feDropShadow dx=\"0\" dy=\"12\" stdDeviation=\"18\" flood-color=\"")
                .append(theme.shadowColor())
                .append("\" flood-opacity=\"")
                .append(theme.shadowOpacity())
                .append("\"/>\n");
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
                .append("\" y=\"62\" fill=\"")
                .append(theme.titleText())
                .append("\" font-size=\"34\" font-weight=\"700\">Coverage over Time</text>\n");
        svg.append("  <text x=\"")
                .append(DASHBOARD_PADDING)
                .append("\" y=\"92\" fill=\"")
                .append(theme.mutedText())
                .append("\" font-size=\"17\">Updated ")
                .append(MetricsChartSupport.escapeXml(formatGeneratedAt(generatedAt)))
                .append("</text>\n");
        for (int index = 0; index < panels.size(); index++) {
            int panelX = DASHBOARD_PADDING + (index % 2) * (PANEL_WIDTH + DASHBOARD_GAP);
            int panelY = DASHBOARD_HEADER_HEIGHT + DASHBOARD_PADDING + (index / 2) * (PANEL_HEIGHT + DASHBOARD_GAP);
            appendPanel(svg, panels.get(index), panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, theme);
        }

        svg.append("</svg>");
        return svg.toString();
    }

    private static List<MetricsChartSupport.MetricPoint> extractMetricPoints(
            ReadmeBadgeSummarySupport.ReadmeMetricsHistory history,
            Function<ReadmeBadgeSummarySupport.HistoryEntry, BigDecimal> valueExtractor
    ) {
        List<MetricsChartSupport.MetricPoint> points = new ArrayList<>();
        if (history == null || history.history() == null) {
            return points;
        }
        for (ReadmeBadgeSummarySupport.HistoryEntry entry : history.history()) {
            if (entry == null || entry.date() == null || entry.metrics() == null) {
                continue;
            }
            BigDecimal value = valueExtractor.apply(entry);
            if (value == null) {
                continue;
            }
            points.add(new MetricsChartSupport.MetricPoint(LocalDate.parse(entry.date()), value));
        }
        return points;
    }

    private static void appendPanel(StringBuilder svg, PanelSpec panel, int panelX, int panelY, int panelWidth, int panelHeight, ThemePalette theme) {
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
                .append("\" rx=\"24\" fill=\"")
                .append(theme.panelFill())
                .append("\" fill-opacity=\"")
                .append(theme.panelOpacity())
                .append("\" stroke=\"")
                .append(theme.panelStroke())
                .append("\" filter=\"url(#panel-shadow)\"/>\n");
        svg.append("    <text x=\"")
                .append(panelX + 24)
                .append("\" y=\"")
                .append(panelY + 34)
                .append("\" fill=\"")
                .append(theme.titleText())
                .append("\" font-size=\"23\" font-weight=\"700\">")
                .append(MetricsChartSupport.escapeXml(panel.title()))
                .append("</text>\n");
        svg.append("    <text x=\"")
                .append(panelX + 24)
                .append("\" y=\"")
                .append(panelY + 56)
                .append("\" fill=\"")
                .append(theme.subtitleText())
                .append("\" font-size=\"15\">")
                .append(MetricsChartSupport.escapeXml(panel.subtitle()))
                .append("</text>\n");

        if (panel.points().isEmpty()) {
            svg.append("    <text x=\"")
                    .append(panelX + (panelWidth / 2))
                    .append("\" y=\"")
                    .append(panelY + (panelHeight / 2))
                    .append("\" fill=\"")
                    .append(theme.xTickText())
                    .append("\" font-size=\"17\" text-anchor=\"middle\">No history entries yet</text>\n");
            svg.append("  </g>\n");
            return;
        }

        MetricsChartSupport.MetricPoint latestPoint = panel.points().get(panel.points().size() - 1);
        MetricsChartSupport.ChartBounds bounds = MetricsChartSupport.calculateBounds(panel.points(), panel.percentMetric());
        List<BigDecimal> yTicks = MetricsChartSupport.buildYAxisTicks(bounds.minValue(), bounds.maxValue());
        List<Integer> xLabelIndexes = MetricsChartSupport.buildXLabelIndexes(panel.points().size());
        List<String> xLabels = MetricsChartSupport.buildXLabels(panel.points(), xLabelIndexes);

        for (BigDecimal tick : yTicks) {
            double y = MetricsChartSupport.toPlotY(tick, bounds.minValue(), bounds.maxValue(), plotY, plotHeight);
            svg.append("    <line x1=\"")
                    .append(plotX)
                    .append("\" y1=\"")
                    .append(MetricsChartSupport.formatDecimal(y))
                    .append("\" x2=\"")
                    .append(plotX + plotWidth)
                    .append("\" y2=\"")
                    .append(MetricsChartSupport.formatDecimal(y))
                    .append("\" stroke=\"")
                    .append(theme.yGridLine())
                    .append("\" stroke-width=\"1\"/>\n");
            svg.append("    <text x=\"")
                    .append(plotX - 10)
                    .append("\" y=\"")
                    .append(MetricsChartSupport.formatDecimal(y + 4))
                    .append("\" fill=\"")
                    .append(theme.yTickText())
                    .append("\" font-size=\"13\" text-anchor=\"end\">")
                    .append(MetricsChartSupport.escapeXml(MetricsChartSupport.formatTickValue(tick, panel.valueFormat())))
                    .append("</text>\n");
        }

        for (int labelIndex = 0; labelIndex < xLabelIndexes.size(); labelIndex++) {
            int pointIndex = xLabelIndexes.get(labelIndex);
            double x = MetricsChartSupport.toPlotX(pointIndex, panel.points().size(), plotX, plotWidth);
            boolean firstLabel = pointIndex == 0;
            boolean lastLabel = pointIndex == panel.points().size() - 1;
            String textAnchor = firstLabel ? "start" : lastLabel ? "end" : "middle";
            double labelX = firstLabel
                    ? x + PANEL_X_LABEL_INSET
                    : lastLabel
                    ? x - PANEL_X_LABEL_INSET
                    : x;
            svg.append("    <line x1=\"")
                    .append(MetricsChartSupport.formatDecimal(x))
                    .append("\" y1=\"")
                    .append(plotY)
                    .append("\" x2=\"")
                    .append(MetricsChartSupport.formatDecimal(x))
                    .append("\" y2=\"")
                    .append(plotBottom)
                    .append("\" stroke=\"")
                    .append(theme.xGridLine())
                    .append("\" stroke-width=\"1\"/>\n");
            svg.append("    <text x=\"")
                    .append(MetricsChartSupport.formatDecimal(labelX))
                    .append("\" y=\"")
                    .append(panelY + panelHeight - 24)
                    .append("\" fill=\"")
                    .append(theme.xTickText())
                    .append("\" font-size=\"13\" text-anchor=\"")
                    .append(textAnchor)
                    .append("\">")
                    .append(MetricsChartSupport.escapeXml(xLabels.get(labelIndex)))
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
                .append("\" rx=\"18\" fill=\"none\" stroke=\"")
                .append(theme.plotFrame())
                .append("\"/>\n");

        String areaPath = MetricsChartSupport.buildAreaPath(panel.points(), bounds.minValue(), bounds.maxValue(), plotX, plotY, plotWidth, plotHeight, plotBottom);
        String linePath = MetricsChartSupport.buildLinePath(panel.points(), bounds.minValue(), bounds.maxValue(), plotX, plotY, plotWidth, plotHeight);
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

        double latestX = MetricsChartSupport.toPlotX(panel.points().size() - 1, panel.points().size(), plotX, plotWidth);
        double latestY = MetricsChartSupport.toPlotY(latestPoint.value(), bounds.minValue(), bounds.maxValue(), plotY, plotHeight);
        svg.append("    <circle cx=\"")
                .append(MetricsChartSupport.formatDecimal(latestX))
                .append("\" cy=\"")
                .append(MetricsChartSupport.formatDecimal(latestY))
                .append("\" r=\"6\" fill=\"")
                .append(theme.pointFill())
                .append("\" stroke=\"")
                .append(panel.primaryColor())
                .append("\" stroke-width=\"3.5\"/>\n");

        String latestValue = MetricsChartSupport.formatMetricValue(latestPoint.value(), panel.valueFormat());
        double badgeWidth = Math.max(78.0d, Math.min(140.0d, 26.0d + (latestValue.length() * 7.2d)));
        double badgeX = Math.max(panelX + 24.0d, Math.min(panelX + panelWidth - badgeWidth - 24.0d, latestX - (badgeWidth / 2.0d)));
        double badgeY = Math.max(panelY + 22.0d, latestY - 40.0d);
        svg.append("    <g transform=\"translate(")
                .append(MetricsChartSupport.formatDecimal(badgeX))
                .append(' ')
                .append(MetricsChartSupport.formatDecimal(badgeY))
                .append(")\">\n");
        svg.append("      <rect width=\"")
                .append(MetricsChartSupport.formatDecimal(badgeWidth))
                .append("\" height=\"28\" rx=\"14\" fill=\"")
                .append(theme.badgeFill())
                .append("\"/>\n");
        svg.append("      <text x=\"")
                .append(MetricsChartSupport.formatDecimal(badgeWidth / 2.0d))
                .append("\" y=\"19\" fill=\"")
                .append(theme.badgeText())
                .append("\" font-size=\"15\" font-weight=\"700\" text-anchor=\"middle\">")
                .append(MetricsChartSupport.escapeXml(latestValue))
                .append("</text>\n");
        svg.append("    </g>\n");
        svg.append("  </g>\n");
    }

    static Path darkMetricsOverviewGraphFile(Path graphFile) {
        String fileName = graphFile.getFileName().toString();
        if (fileName.endsWith(".svg")) {
            return graphFile.resolveSibling(fileName.substring(0, fileName.length() - 4) + "-dark.svg");
        }
        return graphFile.resolveSibling(fileName + "-dark.svg");
    }

    private static String formatGeneratedAt(Instant generatedAt) {
        return GENERATED_AT_FORMATTER.format(generatedAt.truncatedTo(ChronoUnit.MINUTES));
    }

    private record PanelSpec(
            String id,
            String title,
            String subtitle,
            boolean percentMetric,
            String valueFormat,
            String primaryColor,
            String highlightColor,
            List<MetricsChartSupport.MetricPoint> points
    ) {
    }

    record ThemePalette(
            String dashboardStart,
            String dashboardEnd,
            String shadowColor,
            String shadowOpacity,
            String titleText,
            String mutedText,
            String panelFill,
            String panelOpacity,
            String panelStroke,
            String subtitleText,
            String yGridLine,
            String yTickText,
            String xGridLine,
            String xTickText,
            String plotFrame,
            String pointFill,
            String badgeFill,
            String badgeText
    ) {
    }
}
