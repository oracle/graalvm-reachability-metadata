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
            String artifactId,
            String metadataVersion
    ) {
        LibraryStatsModels.ArtifactStats artifactStats = libraryStats.entries().get(artifact);
        if (artifactStats == null) {
            return emptyMetadataVersionStats();
        }
        if (!artifactId.equals(artifactStats.artifactId())) {
            throw new GradleException("Invalid artifactId for " + artifact + ": expected " + artifactId + " but found "
                    + artifactStats.artifactId());
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
            String artifactId,
            String metadataVersion,
            LibraryStatsModels.MetadataVersionStats metadataVersionStats
    ) {
        NavigableMap<String, LibraryStatsModels.ArtifactStats> entries = new TreeMap<>();
        if (libraryStats.entries() != null) {
            entries.putAll(libraryStats.entries());
        }

        LibraryStatsModels.ArtifactStats existingArtifactStats = entries.get(artifact);
        if (existingArtifactStats != null && !artifactId.equals(existingArtifactStats.artifactId())) {
            throw new GradleException("Invalid artifactId for " + artifact + ": expected " + existingArtifactStats.artifactId()
                    + " but received " + artifactId);
        }

        NavigableMap<String, LibraryStatsModels.MetadataVersionStats> metadataVersions = new TreeMap<>();
        if (existingArtifactStats != null && existingArtifactStats.metadataVersions() != null) {
            metadataVersions.putAll(existingArtifactStats.metadataVersions());
        }
        metadataVersions.put(metadataVersion, normalizeMetadataVersionStats(metadataVersionStats));

        entries.put(artifact, new LibraryStatsModels.ArtifactStats(artifactId, metadataVersions));
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

    public static String artifactIdFromArtifact(String artifact) {
        int separatorIndex = artifact.indexOf(':');
        if (separatorIndex < 0 || separatorIndex == artifact.length() - 1) {
            throw new GradleException("Invalid artifact identifier " + artifact);
        }
        return artifact.substring(separatorIndex + 1);
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
                coordinate,
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

            entries.put(artifact, new LibraryStatsModels.ArtifactStats(artifactStats.artifactId(), metadataVersions));
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
            byVersion.put(versionStats.version(), versionStats);
        }
        return new LibraryStatsModels.MetadataVersionStats(new ArrayList<>(byVersion.values()));
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

        Map<String, Set<String>> totalKeysByType = new TreeMap<>();
        Map<String, Set<String>> coveredKeysByType = new TreeMap<>();

        try {
            Files.walk(dynamicAccessDir)
                    .filter(Files::isRegularFile)
                    .filter(path -> DYNAMIC_ACCESS_REPORT.matcher(path.getFileName().toString()).matches())
                    .sorted(Comparator.comparing(path -> path.toAbsolutePath().toString()))
                    .forEach(path -> parseDynamicAccessFile(path, libraryClasses, coveredLinesBySource, totalKeysByType, coveredKeysByType));
        } catch (IOException e) {
            throw new GradleException("Failed to traverse dynamic access directory " + dynamicAccessDir, e);
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

        return new ParsedDynamicAccess(
                new LibraryStatsModels.DynamicAccessStats(totalCalls, coveredCalls, ratio(coveredCalls, totalCalls), breakdown)
        );
    }

    private static ParsedDynamicAccess emptyDynamicAccess() {
        return new ParsedDynamicAccess(
                new LibraryStatsModels.DynamicAccessStats(0, 0, ratio(0, 0), Map.of())
        );
    }

    private static void parseDynamicAccessFile(
            Path path,
            Set<String> libraryClasses,
            Map<String, Set<Integer>> coveredLinesBySource,
            Map<String, Set<String>> totalKeysByType,
            Map<String, Set<String>> coveredKeysByType
    ) {
        Matcher matcher = DYNAMIC_ACCESS_REPORT.matcher(path.getFileName().toString());
        if (!matcher.matches()) {
            return;
        }
        String reportType = matcher.group(1).toLowerCase(Locale.ROOT);

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
                    totalKeysByType.computeIfAbsent(reportType, ignored -> new LinkedHashSet<>()).add(callSiteKey);
                    if (parsedFrame.lineNumber() == null) {
                        continue;
                    }
                    String sourceKey = sourceKey(parsedFrame.className(), parsedFrame.sourceFile());
                    Set<Integer> coveredLines = coveredLinesBySource.get(sourceKey);
                    if (coveredLines != null && coveredLines.contains(parsedFrame.lineNumber())) {
                        coveredKeysByType.computeIfAbsent(reportType, ignored -> new LinkedHashSet<>()).add(callSiteKey);
                    }
                }
            }
        } catch (IOException e) {
            throw new GradleException("Failed to parse dynamic access report " + path, e);
        }
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
                    requireCoverageMetric(rootCounters, "LINE"),
                    requireCoverageMetric(rootCounters, "INSTRUCTION"),
                    requireCoverageMetric(rootCounters, "METHOD")
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

    private static LibraryStatsModels.CoverageMetric requireCoverageMetric(
            Map<String, LibraryStatsModels.CoverageMetric> counters,
            String type
    ) {
        LibraryStatsModels.CoverageMetric coverageMetric = counters.get(type);
        if (coverageMetric == null) {
            throw new GradleException("JaCoCo report is missing root counter type " + type);
        }
        return coverageMetric;
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

    private record ParsedStackFrame(String className, String sourceFile, Integer lineNumber) {
    }

    private record ParsedDynamicAccess(LibraryStatsModels.DynamicAccessStats dynamicAccessStats) {
    }

    private record ParsedJacocoReport(
            Map<String, Set<Integer>> coveredLinesBySource,
            LibraryStatsModels.CoverageMetric line,
            LibraryStatsModels.CoverageMetric instruction,
            LibraryStatsModels.CoverageMetric method
    ) {
    }

    public record ExternalDynamicAccessSummary(
            long totalCalls,
            Map<String, LibraryStatsModels.DynamicAccessBreakdown> breakdown
    ) {
    }
}
