/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.stats;

import com.fasterxml.jackson.core.type.TypeReference;
import org.gradle.api.GradleException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Parses dynamic-access call reports into per-type and per-class coverage.
 */
final class DynamicAccessReportsParser {

    static final Pattern DYNAMIC_ACCESS_REPORT = Pattern.compile("(.+)-calls\\.json");

    private static final Pattern FRAME_PATTERN = Pattern.compile("^(.+)\\.([^.(]+|<init>|<clinit>)\\(([^:()]+)(?::(\\d+))?\\)$");

    private static final Map<String, String> DYNAMIC_ACCESS_TYPE_ALIASES = Map.of(
            "reflection", "reflection",
            "resource", "resources",
            "resources", "resources",
            "jni", "foreign",
            "foreign", "foreign"
    );

    private DynamicAccessReportsParser() {
    }

    static ParsedDynamicAccess parse(
            Path dynamicAccessDir,
            Set<String> libraryClasses,
            Map<String, Set<Integer>> coveredLinesBySource,
            Map<String, Set<String>> methodNamesByClass,
            Set<CoverageMeasurement.AgentCoveredCallSite> agentCoveredCallSites
    ) {
        if (!Files.isDirectory(dynamicAccessDir)) {
            return emptyDynamicAccess();
        }

        Map<String, ListedCallSite> listedCallSitesByKey = new LinkedHashMap<>();

        try {
            Files.walk(dynamicAccessDir)
                    .filter(Files::isRegularFile)
                    .filter(path -> DYNAMIC_ACCESS_REPORT.matcher(path.getFileName().toString()).matches())
                    .sorted(Comparator.comparing(path -> path.toAbsolutePath().toString()))
                    .forEach(path -> parseDynamicAccessFile(
                            path, libraryClasses, coveredLinesBySource, methodNamesByClass, listedCallSitesByKey));
        } catch (IOException e) {
            throw new GradleException("Failed to traverse dynamic access directory " + dynamicAccessDir, e);
        }

        if (listedCallSitesByKey.isEmpty()) {
            return emptyDynamicAccess();
        }

        // §AR-test-harness.8.1: the agent key cannot tell sites of one method apart, so a key that
        // also names a line-decided site lends no agent evidence to an undecided one.
        Set<CoverageMeasurement.AgentCoveredCallSite> lineDecidedKeys = listedCallSitesByKey.values().stream()
                .filter(callSite -> callSite.lineDecision() != LineDecision.UNDECIDED)
                .map(ListedCallSite::agentKey)
                .collect(Collectors.toSet());
        Map<String, ParsedDynamicAccessCallSite> callSitesByKey = new LinkedHashMap<>();
        long lineUndecidedCalls = 0L;
        for (Map.Entry<String, ListedCallSite> entry : listedCallSitesByKey.entrySet()) {
            ListedCallSite listed = entry.getValue();
            boolean agentDecidable = listed.lineDecision() == LineDecision.UNDECIDED
                    && !lineDecidedKeys.contains(listed.agentKey());
            if (agentDecidable) {
                lineUndecidedCalls++;
            }
            boolean covered = listed.lineDecision() == LineDecision.COVERED
                    || agentDecidable && agentCoveredCallSites.contains(listed.agentKey());
            callSitesByKey.put(entry.getKey(), new ParsedDynamicAccessCallSite(
                    listed.metadataType(),
                    listed.trackedApi(),
                    listed.frame(),
                    listed.parsedFrame().className(),
                    listed.parsedFrame().sourceFile(),
                    listed.parsedFrame().lineNumber(),
                    covered
            ));
        }

        Map<String, Set<String>> totalKeysByType = new TreeMap<>();
        Map<String, Set<String>> coveredKeysByType = new TreeMap<>();
        Map<String, List<ParsedDynamicAccessCallSite>> callSitesByClass = new TreeMap<>();

        for (Map.Entry<String, ParsedDynamicAccessCallSite> entry : callSitesByKey.entrySet()) {
            ParsedDynamicAccessCallSite callSite = entry.getValue();
            totalKeysByType.computeIfAbsent(callSite.metadataType(), ignored -> new LinkedHashSet<>()).add(entry.getKey());
            if (callSite.covered()) {
                coveredKeysByType.computeIfAbsent(callSite.metadataType(), ignored -> new LinkedHashSet<>()).add(entry.getKey());
            }
            callSitesByClass.computeIfAbsent(callSite.className(), ignored -> new ArrayList<>()).add(callSite);
        }

        long totalCalls = totalKeysByType.values().stream().mapToLong(Set::size).sum();
        long coveredCalls = coveredKeysByType.values().stream().mapToLong(Set::size).sum();

        Map<String, LibraryStatsModels.DynamicAccessBreakdown> breakdown = new TreeMap<>();
        for (Map.Entry<String, Set<String>> entry : totalKeysByType.entrySet()) {
            String reportType = entry.getKey();
            long total = entry.getValue().size();
            long covered = coveredKeysByType.getOrDefault(reportType, Set.of()).size();
            breakdown.put(reportType, new LibraryStatsModels.DynamicAccessBreakdown(total, covered, LibraryStatsSupport.ratio(covered, total)));
        }

        List<LibraryStatsModels.DynamicAccessClassCoverage> classCoverage = callSitesByClass.entrySet().stream()
                .map(entry -> toClassCoverage(entry.getKey(), entry.getValue()))
                .sorted(Comparator
                        .comparingLong((LibraryStatsModels.DynamicAccessClassCoverage value) -> value.totalCalls() - value.coveredCalls())
                        .reversed()
                        .thenComparing(LibraryStatsModels.DynamicAccessClassCoverage::className))
                .toList();

        return new ParsedDynamicAccess(
                new LibraryStatsModels.DynamicAccessStats(totalCalls, coveredCalls, LibraryStatsSupport.ratio(coveredCalls, totalCalls), breakdown),
                classCoverage,
                lineUndecidedCalls
        );
    }

    private static ParsedDynamicAccess emptyDynamicAccess() {
        return new ParsedDynamicAccess(
                LibraryStatsSupport.emptyDynamicAccessStats(),
                List.of(),
                0L
        );
    }

    private static void parseDynamicAccessFile(
            Path path,
            Set<String> libraryClasses,
            Map<String, Set<Integer>> coveredLinesBySource,
            Map<String, Set<String>> methodNamesByClass,
            Map<String, ListedCallSite> listedCallSitesByKey
    ) {
        Matcher matcher = DYNAMIC_ACCESS_REPORT.matcher(path.getFileName().toString());
        if (!matcher.matches()) {
            return;
        }
        String reportType = normalizeDynamicAccessReportType(matcher.group(1), path);

        try (InputStream inputStream = Files.newInputStream(path)) {
            Map<String, List<String>> report = LibraryStatsSupport.OBJECT_MAPPER.readValue(inputStream, new TypeReference<>() {
            });
            for (Map.Entry<String, List<String>> entry : report.entrySet()) {
                String trackedApi = entry.getKey();
                for (String rawFrame : entry.getValue()) {
                    ParsedStackFrame parsedFrame = parseStackFrame(rawFrame);
                    if (parsedFrame == null || !libraryClasses.contains(parsedFrame.className())) {
                        continue;
                    }
                    String callSiteKey = reportType + '\u0000' + trackedApi + '\u0000' + rawFrame;
                    listedCallSitesByKey.computeIfAbsent(callSiteKey, ignored -> new ListedCallSite(
                            reportType,
                            trackedApi,
                            rawFrame,
                            parsedFrame,
                            lineDecision(parsedFrame, coveredLinesBySource, methodNamesByClass)
                    ));
                }
            }
        } catch (IOException e) {
            throw new GradleException("Failed to parse dynamic access report " + path, e);
        }
    }

    /// §AR-test-harness.8.1: line matching decides a frame by its line unless the frame is
    /// line-less or sits in a method JaCoCo filtered out of an analyzed class, such as a synthetic
    /// `class$` helper. A class JaCoCo never analyzed, as in an upstream test JAR, has no
    /// `methodNamesByClass` entry, so its frames stay decided by line.
    private static LineDecision lineDecision(
            ParsedStackFrame frame,
            Map<String, Set<Integer>> coveredLinesBySource,
            Map<String, Set<String>> methodNamesByClass
    ) {
        if (frame.lineNumber() == null) {
            return LineDecision.UNDECIDED;
        }
        Set<String> analyzedMethodNames = methodNamesByClass.get(frame.className());
        if (analyzedMethodNames != null && !analyzedMethodNames.contains(frame.methodName())) {
            return LineDecision.UNDECIDED;
        }
        boolean covered = coveredLinesBySource.getOrDefault(sourceKey(frame.className(), frame.sourceFile()), Set.of())
                .contains(frame.lineNumber());
        return covered ? LineDecision.COVERED : LineDecision.MISSED;
    }

    private static String normalizeDynamicAccessReportType(String rawType, Path path) {
        String normalizedRawType = rawType.toLowerCase(Locale.ROOT);
        String normalizedType = DYNAMIC_ACCESS_TYPE_ALIASES.get(normalizedRawType);
        if (normalizedType != null) {
            return normalizedType;
        }
        throw new GradleException(
                "Unsupported dynamic access report type '" + rawType + "' in " + path
                        + ". Supported types are reflection, resources, and foreign."
        );
    }

    static ParsedStackFrame parseStackFrame(String rawFrame) {
        Matcher matcher = FRAME_PATTERN.matcher(rawFrame);
        if (!matcher.matches()) {
            return null;
        }
        String className = matcher.group(1);
        String methodName = matcher.group(2);
        String sourceFile = matcher.group(3);
        String lineNumberString = matcher.group(4);
        Integer lineNumber = lineNumberString == null ? null : Integer.parseInt(lineNumberString);
        return new ParsedStackFrame(className, methodName, sourceFile, lineNumber);
    }

    private static String sourceKey(String className, String sourceFile) {
        int separatorIndex = className.lastIndexOf('.');
        if (separatorIndex < 0) {
            return sourceFile;
        }
        String packageName = className.substring(0, separatorIndex).replace('.', '/');
        return packageName + "/" + sourceFile;
    }

    private static LibraryStatsModels.DynamicAccessClassCoverage toClassCoverage(
            String className,
            List<ParsedDynamicAccessCallSite> callSites
    ) {
        List<LibraryStatsModels.DynamicAccessCallSiteCoverage> sortedCallSites = callSites.stream()
                .sorted(Comparator
                        .comparing(ParsedDynamicAccessCallSite::metadataType)
                        .thenComparing(ParsedDynamicAccessCallSite::trackedApi)
                        .thenComparing(ParsedDynamicAccessCallSite::frame))
                .map(callSite -> new LibraryStatsModels.DynamicAccessCallSiteCoverage(
                        callSite.metadataType(),
                        callSite.trackedApi(),
                        callSite.frame(),
                        callSite.line(),
                        callSite.covered()
                ))
                .toList();

        long coveredCalls = callSites.stream().filter(ParsedDynamicAccessCallSite::covered).count();
        String sourceFile = callSites.stream()
                .map(ParsedDynamicAccessCallSite::sourceFile)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);

        return new LibraryStatsModels.DynamicAccessClassCoverage(
                className,
                sourceFile,
                callSites.size(),
                coveredCalls,
                sortedCallSites
        );
    }

    record ParsedStackFrame(String className, String methodName, String sourceFile, Integer lineNumber) {
    }

    /// `lineUndecidedCalls` counts the call sites agent origins can still decide: line matching
    /// cannot, and no line-decided site shares their agent key (§AR-test-harness.8.1).
    record ParsedDynamicAccess(
            LibraryStatsModels.DynamicAccessStats dynamicAccessStats,
            List<LibraryStatsModels.DynamicAccessClassCoverage> classCoverage,
            long lineUndecidedCalls
    ) {
    }

    private enum LineDecision {
        COVERED,
        MISSED,
        UNDECIDED
    }

    /// A reported call site with its line decision, before agent evidence is applied.
    private record ListedCallSite(
            String metadataType,
            String trackedApi,
            String frame,
            ParsedStackFrame parsedFrame,
            LineDecision lineDecision
    ) {
        CoverageMeasurement.AgentCoveredCallSite agentKey() {
            return new CoverageMeasurement.AgentCoveredCallSite(trackedApi, parsedFrame.className(), parsedFrame.methodName());
        }
    }

    private record ParsedDynamicAccessCallSite(
            String metadataType,
            String trackedApi,
            String frame,
            String className,
            String sourceFile,
            Integer line,
            boolean covered
    ) {
    }
}
