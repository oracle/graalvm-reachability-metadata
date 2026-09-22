/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.stats;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * Chart geometry, axis, and value-formatting primitives for metric graphs.
 */
final class MetricsChartSupport {

    private static final BigDecimal PERCENT_MIN_RANGE = new BigDecimal("10.0");
    private static final BigDecimal PERCENT_PADDING = new BigDecimal("2.0");
    private static final BigDecimal PERCENT_TICK_STEP = new BigDecimal("5.0");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private MetricsChartSupport() {
    }

    static ChartBounds calculateBounds(List<MetricPoint> points, boolean percentMetric) {
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

    static List<BigDecimal> buildYAxisTicks(BigDecimal minValue, BigDecimal maxValue) {
        List<BigDecimal> ticks = new ArrayList<>();
        BigDecimal step = maxValue.subtract(minValue).divide(BigDecimal.valueOf(4), 1, RoundingMode.HALF_UP);
        for (int index = 0; index < 5; index++) {
            BigDecimal tick = minValue.add(step.multiply(BigDecimal.valueOf(index))).setScale(1, RoundingMode.HALF_UP);
            ticks.add(tick);
        }
        return ticks;
    }

    static List<Integer> buildXLabelIndexes(int pointCount) {
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

    static List<String> buildXLabels(List<MetricPoint> points, List<Integer> xLabelIndexes) {
        List<LocalDate> labelDates = new ArrayList<>();
        for (Integer pointIndex : xLabelIndexes) {
            labelDates.add(points.get(pointIndex).date());
        }

        List<String> monthYearLabels = labelDates.stream()
                .map(MetricsChartSupport::formatMonthYear)
                .toList();
        if (labelsAreUnique(monthYearLabels)) {
            return monthYearLabels;
        }

        List<String> monthDayLabels = labelDates.stream()
                .map(MetricsChartSupport::formatMonthDay)
                .toList();
        if (labelsAreUnique(monthDayLabels)) {
            return monthDayLabels;
        }

        return labelDates.stream()
                .map(LocalDate::toString)
                .toList();
    }

    private static boolean labelsAreUnique(List<String> labels) {
        return new HashSet<>(labels).size() == labels.size();
    }

    static String buildLinePath(
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

    static String buildAreaPath(
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

    static double toPlotX(int index, int pointCount, int plotX, int plotWidth) {
        if (pointCount <= 1) {
            return plotX + (plotWidth / 2.0d);
        }
        return plotX + ((double) index / (pointCount - 1)) * plotWidth;
    }

    static double toPlotY(BigDecimal value, BigDecimal minValue, BigDecimal maxValue, int plotY, int plotHeight) {
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

    private static String formatMonthYear(LocalDate date) {
        return formatShortMonth(date)
                + " "
                + date.getYear();
    }

    private static String formatMonthDay(LocalDate date) {
        return formatShortMonth(date)
                + " "
                + date.getDayOfMonth();
    }

    private static String formatShortMonth(LocalDate date) {
        return date.getMonth().name().substring(0, 1)
                + date.getMonth().name().substring(1, 3).toLowerCase(Locale.ROOT);
    }

    static String formatTickValue(BigDecimal value, String valueFormat) {
        if ("percent".equals(valueFormat)) {
            return formatPercent(value);
        }
        return formatCompactInteger(value.setScale(0, RoundingMode.HALF_UP).longValue());
    }

    static String formatMetricValue(BigDecimal value, String valueFormat) {
        if ("percent".equals(valueFormat)) {
            return formatPercent(value);
        }
        return formatInteger(value.setScale(0, RoundingMode.HALF_UP).intValue());
    }

    static String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    static String formatDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    static String formatInteger(int value) {
        return formatInteger((long) value);
    }

    static String formatInteger(long value) {
        NumberFormat format = NumberFormat.getIntegerInstance(Locale.US);
        format.setGroupingUsed(true);
        return format.format(value);
    }

    private static String formatCompactInteger(long value) {
        long absoluteValue = Math.abs(value);
        if (absoluteValue >= 1_000_000L) {
            return formatCompactDecimal(value, 1_000_000L, "M");
        }
        if (absoluteValue >= 100_000L) {
            return formatCompactDecimal(value, 1_000L, "K");
        }
        return formatInteger(value);
    }

    private static String formatCompactDecimal(long value, long divisor, String suffix) {
        BigDecimal compactValue = BigDecimal.valueOf(value)
                .divide(BigDecimal.valueOf(divisor), 1, RoundingMode.HALF_UP)
                .stripTrailingZeros();
        return compactValue.toPlainString() + suffix;
    }

    static String formatPercent(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    record MetricPoint(
            LocalDate date,
            BigDecimal value
    ) {
    }

    record ChartBounds(
            BigDecimal minValue,
            BigDecimal maxValue
    ) {
    }
}
