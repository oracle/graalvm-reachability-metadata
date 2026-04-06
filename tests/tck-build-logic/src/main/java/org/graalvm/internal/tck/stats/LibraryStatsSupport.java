/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.stats;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.graalvm.internal.tck.Coordinates;
import org.gradle.api.GradleException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Parsing, merge, and persistence helpers for library stats.
 */
public final class LibraryStatsSupport {

    private static final TypeReference<LibraryStatsModels.LibraryStats> LIBRARY_STATS_TYPE = new TypeReference<>() {
    };

    private static final Pattern DYNAMIC_ACCESS_REPORT = Pattern.compile("(.+)-calls\\.json");

    private static final Pattern FRAME_PATTERN = Pattern.compile("^(.+)\\.([^.(]+|<init>|<clinit>)\\(([^:()]+)(?::(\\d+))?\\)$");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private static final int RATIO_SCALE = 6;
    private static final Map<String, String> DYNAMIC_ACCESS_TYPE_ALIASES = Map.of(
            "reflection", "reflection",
            "resource", "resources",
            "resources", "resources",
            "jni", "foreign",
            "foreign", "foreign"
    );

    private LibraryStatsSupport() {
    }

    public static LibraryStatsModels.LibraryStats loadStats(Path statsFile) {
        if (!Files.exists(statsFile)) {
            return emptyLibraryStats();
        }
        try {
            return normalizeLibraryStats(OBJECT_MAPPER.readValue(statsFile.toFile(), LIBRARY_STATS_TYPE));
        } catch (IOException e) {
            throw new GradleException("Failed to read library stats from " + statsFile, e);
        }
    }

    public static void writeStats(Path statsFile, LibraryStatsModels.LibraryStats libraryStats) {
        writeJsonWithTrailingNewline(statsFile, normalizeLibraryStats(libraryStats), "Failed to write library stats to ");
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

    public static LibraryStatsModels.MetadataVersionStats mergeStats(
            LibraryStatsModels.MetadataVersionStats existing,
            Collection<LibraryStatsModels.VersionStats> updates,
            boolean replaceAllVersions
    ) {
        NavigableMap<String, LibraryStatsModels.VersionStats> byVersion = new TreeMap<>();
        if (existing != null && existing.versions() != null) {
            for (LibraryStatsModels.VersionStats versionStats : existing.versions()) {
                byVersion.put(versionStats.version(), versionStats);
            }
        }

        if (replaceAllVersions) {
            byVersion.clear();
        }

        for (LibraryStatsModels.VersionStats versionStats : updates) {
            byVersion.put(versionStats.version(), versionStats);
        }

        return new LibraryStatsModels.MetadataVersionStats(new ArrayList<>(byVersion.values()));
    }

    public static LibraryStatsModels.MetadataVersionStats metadataVersionStats(
            LibraryStatsModels.LibraryStats libraryStats,
            String artifact,
            String metadataVersion
    ) {
        LibraryStatsModels.ArtifactStats artifactStats = libraryStats.entries().get(artifact);
        if (artifactStats == null) {
            return emptyMetadataVersionStats();
        }
        LibraryStatsModels.MetadataVersionStats metadataVersionStats = artifactStats.metadataVersions().get(metadataVersion);
        if (metadataVersionStats == null) {
            return emptyMetadataVersionStats();
        }
        return normalizeMetadataVersionStats(metadataVersionStats);
    }

    public static LibraryStatsModels.LibraryStats withMetadataVersionStats(
            LibraryStatsModels.LibraryStats libraryStats,
            String artifact,
            String metadataVersion,
            LibraryStatsModels.MetadataVersionStats metadataVersionStats
    ) {
        NavigableMap<String, LibraryStatsModels.ArtifactStats> entries = new TreeMap<>();
        if (libraryStats.entries() != null) {
            entries.putAll(libraryStats.entries());
        }

        LibraryStatsModels.ArtifactStats existingArtifactStats = entries.get(artifact);
        NavigableMap<String, LibraryStatsModels.MetadataVersionStats> metadataVersions = new TreeMap<>();
        if (existingArtifactStats != null && existingArtifactStats.metadataVersions() != null) {
            metadataVersions.putAll(existingArtifactStats.metadataVersions());
        }
        metadataVersions.put(metadataVersion, normalizeMetadataVersionStats(metadataVersionStats));

        entries.put(artifact, new LibraryStatsModels.ArtifactStats(metadataVersions));
        return new LibraryStatsModels.LibraryStats(entries);
    }

    public static LibraryStatsModels.VersionStats requireVersionStats(LibraryStatsModels.MetadataVersionStats metadataVersionStats, String coordinate) {
        String version = versionFromCoordinate(coordinate);
        return metadataVersionStats.versions().stream()
                .filter(value -> version.equals(value.version()))
                .findFirst()
                .orElseThrow(() -> new GradleException("Missing library stats entry for coordinate " + coordinate));
    }

    public static String artifactFromCoordinate(String coordinate) {
        Coordinates parsed = Coordinates.parse(coordinate);
        return parsed.group() + ":" + parsed.artifact();
    }

    public static String versionFromCoordinate(String coordinate) {
        return Coordinates.parse(coordinate).version();
    }

    public static LibraryStatsModels.VersionStats buildVersionStats(
            String coordinate,
            List<Path> libraryJars,
            Path dynamicAccessDir,
            Path jacocoReport
    ) {
        Set<String> libraryClasses = loadLibraryClasses(libraryJars);
        ParsedJacocoReport parsedJacocoReport = parseJacocoReport(jacocoReport);
        ParsedDynamicAccess parsedDynamicAccess = parseDynamicAccessReports(dynamicAccessDir, libraryClasses, parsedJacocoReport.coveredLinesBySource());
        return new LibraryStatsModels.VersionStats(
                versionFromCoordinate(coordinate),
                parsedDynamicAccess.dynamicAccessStats(),
                new LibraryStatsModels.LibraryCoverage(
                        parsedJacocoReport.line(),
                        parsedJacocoReport.instruction(),
                        parsedJacocoReport.method()
                )
        );
    }

    public static ExternalDynamicAccessSummary buildExternalDynamicAccessSummary(List<Path> libraryJars, Path dynamicAccessDir) {
        Set<String> libraryClasses = loadLibraryClasses(libraryJars);
        ParsedDynamicAccess parsedDynamicAccess = parseDynamicAccessReports(dynamicAccessDir, libraryClasses, Map.of());
        return new ExternalDynamicAccessSummary(
                parsedDynamicAccess.dynamicAccessStats().totalCalls(),
                parsedDynamicAccess.dynamicAccessStats().breakdown()
        );
    }

    public static LibraryStatsModels.DynamicAccessCoverageReport buildDynamicAccessCoverageReport(
            String coordinate,
            List<Path> libraryJars,
            Path dynamicAccessDir,
            Path jacocoReport
    ) {
        Set<String> libraryClasses = loadLibraryClasses(libraryJars);
        ParsedJacocoReport parsedJacocoReport = parseJacocoReport(jacocoReport);
        ParsedDynamicAccess parsedDynamicAccess = parseDynamicAccessReports(dynamicAccessDir, libraryClasses, parsedJacocoReport.coveredLinesBySource());
        return new LibraryStatsModels.DynamicAccessCoverageReport(
                coordinate,
                parsedDynamicAccess.dynamicAccessStats().totalCalls() > 0,
                new LibraryStatsModels.DynamicAccessCoverageTotals(
                        parsedDynamicAccess.dynamicAccessStats().totalCalls(),
                        parsedDynamicAccess.dynamicAccessStats().coveredCalls()
                ),
                parsedDynamicAccess.classCoverage()
        );
    }

    public static JsonNode toJsonTree(Object value) {
        return OBJECT_MAPPER.valueToTree(value);
    }

    public static String toJsonString(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (IOException e) {
            throw new GradleException("Failed to serialize library stats JSON", e);
        }
    }

    public static String toNormalizedPrettyJsonWithTrailingNewline(LibraryStatsModels.LibraryStats libraryStats) {
        try {
            String json = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(normalizeLibraryStats(libraryStats));
            return json.endsWith(System.lineSeparator()) ? json : json + System.lineSeparator();
        } catch (IOException e) {
            throw new GradleException("Failed to serialize normalized library stats JSON", e);
        }
    }

    public static void writeJson(Path targetFile, Object value) {
        writeJsonWithTrailingNewline(targetFile, value, "Failed to write JSON to ");
    }

    private static LibraryStatsModels.LibraryStats emptyLibraryStats() {
        return new LibraryStatsModels.LibraryStats(new TreeMap<>());
    }

    private static LibraryStatsModels.MetadataVersionStats emptyMetadataVersionStats() {
        return new LibraryStatsModels.MetadataVersionStats(List.of());
    }

    private static LibraryStatsModels.LibraryStats normalizeLibraryStats(LibraryStatsModels.LibraryStats libraryStats) {
        if (libraryStats == null || libraryStats.entries() == null) {
            return emptyLibraryStats();
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

    private static LibraryStatsModels.MetadataVersionStats normalizeMetadataVersionStats(
            LibraryStatsModels.MetadataVersionStats metadataVersionStats
    ) {
        if (metadataVersionStats == null || metadataVersionStats.versions() == null) {
            return emptyMetadataVersionStats();
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

        return LibraryStatsModels.CoverageMetricValue.available(new LibraryStatsModels.CoverageMetric(
                coverageMetricValue.covered(),
                coverageMetricValue.missed(),
                coverageMetricValue.total(),
                normalizeRatio(coverageMetricValue.ratio())
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

    private static Set<String> loadLibraryClasses(List<Path> libraryJars) {
        Set<String> classes = new LinkedHashSet<>();
        for (Path jarPath : libraryJars) {
            try (JarFile jarFile = new JarFile(jarPath.toFile())) {
                jarFile.stream()
                        .map(JarEntry::getName)
                        .filter(name -> name.endsWith(".class"))
                        .filter(name -> !name.equals("module-info.class"))
                        .map(LibraryStatsSupport::classNameFromEntry)
                        .forEach(classes::add);
            } catch (IOException e) {
                throw new GradleException("Failed to read library JAR " + jarPath, e);
            }
        }
        if (classes.isEmpty()) {
            throw new GradleException("No class files were found in resolved library JARs: " + libraryJars);
        }
        return classes;
    }

    private static String classNameFromEntry(String entryName) {
        String normalized = entryName;
        if (entryName.startsWith("META-INF/versions/")) {
            String[] segments = entryName.split("/", 4);
            normalized = segments[3];
        }
        return normalized.substring(0, normalized.length() - ".class".length()).replace('/', '.');
    }

    private static ParsedDynamicAccess parseDynamicAccessReports(
            Path dynamicAccessDir,
            Set<String> libraryClasses,
            Map<String, Set<Integer>> coveredLinesBySource
    ) {
        if (!Files.isDirectory(dynamicAccessDir)) {
            return emptyDynamicAccess();
        }

        Map<String, ParsedDynamicAccessCallSite> callSitesByKey = new LinkedHashMap<>();

        try {
            Files.walk(dynamicAccessDir)
                    .filter(Files::isRegularFile)
                    .filter(path -> DYNAMIC_ACCESS_REPORT.matcher(path.getFileName().toString()).matches())
                    .sorted(Comparator.comparing(path -> path.toAbsolutePath().toString()))
                    .forEach(path -> parseDynamicAccessFile(path, libraryClasses, coveredLinesBySource, callSitesByKey));
        } catch (IOException e) {
            throw new GradleException("Failed to traverse dynamic access directory " + dynamicAccessDir, e);
        }

        if (callSitesByKey.isEmpty()) {
            return emptyDynamicAccess();
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
            breakdown.put(reportType, new LibraryStatsModels.DynamicAccessBreakdown(total, covered, ratio(covered, total)));
        }

        List<LibraryStatsModels.DynamicAccessClassCoverage> classCoverage = callSitesByClass.entrySet().stream()
                .map(entry -> toClassCoverage(entry.getKey(), entry.getValue()))
                .sorted(Comparator
                        .comparingLong((LibraryStatsModels.DynamicAccessClassCoverage value) -> value.totalCalls() - value.coveredCalls())
                        .reversed()
                        .thenComparing(LibraryStatsModels.DynamicAccessClassCoverage::className))
                .toList();

        return new ParsedDynamicAccess(
                new LibraryStatsModels.DynamicAccessStats(totalCalls, coveredCalls, ratio(coveredCalls, totalCalls), breakdown),
                classCoverage
        );
    }

    private static ParsedDynamicAccess emptyDynamicAccess() {
        return new ParsedDynamicAccess(
                new LibraryStatsModels.DynamicAccessStats(0, 0, ratio(0, 0), Map.of()),
                List.of()
        );
    }

    private static void parseDynamicAccessFile(
            Path path,
            Set<String> libraryClasses,
            Map<String, Set<Integer>> coveredLinesBySource,
            Map<String, ParsedDynamicAccessCallSite> callSitesByKey
    ) {
        Matcher matcher = DYNAMIC_ACCESS_REPORT.matcher(path.getFileName().toString());
        if (!matcher.matches()) {
            return;
        }
        String reportType = normalizeDynamicAccessReportType(matcher.group(1), path);

        try (InputStream inputStream = Files.newInputStream(path)) {
            Map<String, List<String>> report = OBJECT_MAPPER.readValue(inputStream, new TypeReference<>() {
            });
            for (Map.Entry<String, List<String>> entry : report.entrySet()) {
                String trackedApi = entry.getKey();
                for (String rawFrame : entry.getValue()) {
                    ParsedStackFrame parsedFrame = parseStackFrame(rawFrame);
                    if (parsedFrame == null || !libraryClasses.contains(parsedFrame.className())) {
                        continue;
                    }
                    String callSiteKey = reportType + '\u0000' + trackedApi + '\u0000' + rawFrame;
                    if (callSitesByKey.containsKey(callSiteKey)) {
                        continue;
                    }
                    boolean covered = false;
                    if (parsedFrame.lineNumber() != null) {
                        String sourceKey = sourceKey(parsedFrame.className(), parsedFrame.sourceFile());
                        Set<Integer> coveredLines = coveredLinesBySource.get(sourceKey);
                        covered = coveredLines != null && coveredLines.contains(parsedFrame.lineNumber());
                    }
                    callSitesByKey.put(
                            callSiteKey,
                            new ParsedDynamicAccessCallSite(
                                    reportType,
                                    trackedApi,
                                    rawFrame,
                                    parsedFrame.className(),
                                    parsedFrame.sourceFile(),
                                    parsedFrame.lineNumber(),
                                    covered
                            )
                    );
                }
            }
        } catch (IOException e) {
            throw new GradleException("Failed to parse dynamic access report " + path, e);
        }
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

    private static ParsedStackFrame parseStackFrame(String rawFrame) {
        Matcher matcher = FRAME_PATTERN.matcher(rawFrame);
        if (!matcher.matches()) {
            return null;
        }
        String className = matcher.group(1);
        String sourceFile = matcher.group(3);
        String lineNumberString = matcher.group(4);
        Integer lineNumber = lineNumberString == null ? null : Integer.parseInt(lineNumberString);
        return new ParsedStackFrame(className, sourceFile, lineNumber);
    }

    private static ParsedJacocoReport parseJacocoReport(Path jacocoReport) {
        if (!Files.isRegularFile(jacocoReport)) {
            throw new GradleException("Missing JaCoCo report " + jacocoReport);
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(jacocoReport.toFile());
            Element root = document.getDocumentElement();

            Map<String, Set<Integer>> coveredLinesBySource = new HashMap<>();
            NodeList packages = root.getChildNodes();
            for (int i = 0; i < packages.getLength(); i++) {
                Node node = packages.item(i);
                if (!(node instanceof Element packageElement) || !"package".equals(packageElement.getTagName())) {
                    continue;
                }
                String packageName = packageElement.getAttribute("name");
                NodeList sourceFiles = packageElement.getChildNodes();
                for (int j = 0; j < sourceFiles.getLength(); j++) {
                    Node sourceNode = sourceFiles.item(j);
                    if (!(sourceNode instanceof Element sourceFileElement) || !"sourcefile".equals(sourceFileElement.getTagName())) {
                        continue;
                    }
                    String sourceName = sourceFileElement.getAttribute("name");
                    String sourceKey = packageName == null || packageName.isEmpty() ? sourceName : packageName + "/" + sourceName;
                    Set<Integer> coveredLines = new LinkedHashSet<>();
                    NodeList lineNodes = sourceFileElement.getChildNodes();
                    for (int k = 0; k < lineNodes.getLength(); k++) {
                        Node lineNode = lineNodes.item(k);
                        if (!(lineNode instanceof Element lineElement) || !"line".equals(lineElement.getTagName())) {
                            continue;
                        }
                        int coveredInstructions = Integer.parseInt(lineElement.getAttribute("ci"));
                        if (coveredInstructions > 0) {
                            coveredLines.add(Integer.parseInt(lineElement.getAttribute("nr")));
                        }
                    }
                    coveredLinesBySource.put(sourceKey, coveredLines);
                }
            }

            Map<String, LibraryStatsModels.CoverageMetric> rootCounters = parseRootCounters(root);
            return new ParsedJacocoReport(
                    coveredLinesBySource,
                    coverageMetricOrNa(rootCounters, "LINE"),
                    coverageMetricOrNa(rootCounters, "INSTRUCTION"),
                    coverageMetricOrNa(rootCounters, "METHOD")
            );
        } catch (Exception e) {
            throw new GradleException("Failed to parse JaCoCo report " + jacocoReport, e);
        }
    }

    private static Map<String, LibraryStatsModels.CoverageMetric> parseRootCounters(Element root) {
        Map<String, LibraryStatsModels.CoverageMetric> counters = new LinkedHashMap<>();
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (!(node instanceof Element counterElement) || !"counter".equals(counterElement.getTagName())) {
                continue;
            }
            String type = counterElement.getAttribute("type");
            long missed = Long.parseLong(counterElement.getAttribute("missed"));
            long covered = Long.parseLong(counterElement.getAttribute("covered"));
            long total = missed + covered;
            counters.put(type, new LibraryStatsModels.CoverageMetric(covered, missed, total, ratio(covered, total)));
        }
        return counters;
    }

    private static LibraryStatsModels.CoverageMetricValue coverageMetricOrNa(
            Map<String, LibraryStatsModels.CoverageMetric> counters,
            String type
    ) {
        LibraryStatsModels.CoverageMetric coverageMetric = counters.get(type);
        if (coverageMetric == null) {
            return LibraryStatsModels.CoverageMetricValue.notAvailable();
        }
        return LibraryStatsModels.CoverageMetricValue.available(coverageMetric);
    }

    private static String sourceKey(String className, String sourceFile) {
        int separatorIndex = className.lastIndexOf('.');
        if (separatorIndex < 0) {
            return sourceFile;
        }
        String packageName = className.substring(0, separatorIndex).replace('.', '/');
        return packageName + "/" + sourceFile;
    }

    private static BigDecimal ratio(long covered, long total) {
        if (total == 0L) {
            return BigDecimal.ZERO.setScale(RATIO_SCALE, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(covered)
                .divide(BigDecimal.valueOf(total), RATIO_SCALE, RoundingMode.HALF_UP);
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

    private record ParsedStackFrame(String className, String sourceFile, Integer lineNumber) {
    }

    private record ParsedDynamicAccess(
            LibraryStatsModels.DynamicAccessStats dynamicAccessStats,
            List<LibraryStatsModels.DynamicAccessClassCoverage> classCoverage
    ) {
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

    private record ParsedJacocoReport(
            Map<String, Set<Integer>> coveredLinesBySource,
            LibraryStatsModels.CoverageMetricValue line,
            LibraryStatsModels.CoverageMetricValue instruction,
            LibraryStatsModels.CoverageMetricValue method
    ) {
    }

    public record ExternalDynamicAccessSummary(
            long totalCalls,
            Map<String, LibraryStatsModels.DynamicAccessBreakdown> breakdown
    ) {
    }
}
