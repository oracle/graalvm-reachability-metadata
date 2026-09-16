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

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Parsing, merge, and persistence helpers for library stats.
 */
public final class LibraryStatsSupport {

    public static final String DYNAMIC_ACCESSES_METRIC = "dynamic-accesses";

    private static final TypeReference<LibraryStatsModels.LibraryStats> LIBRARY_STATS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<LibraryStatsModels.MetadataVersionStats> METADATA_VERSION_STATS_TYPE = new TypeReference<>() {
    };

    static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
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

    public static LibraryStatsModels.MetadataVersionStats loadMetadataVersionStats(Path statsFile) {
        if (!Files.exists(statsFile)) {
            return emptyMetadataVersionStats();
        }
        try {
            return normalizeMetadataVersionStats(OBJECT_MAPPER.readValue(statsFile.toFile(), METADATA_VERSION_STATS_TYPE));
        } catch (IOException e) {
            throw new GradleException("Failed to read metadata-version stats from " + statsFile, e);
        }
    }

    public static void writeMetadataVersionStats(Path statsFile, LibraryStatsModels.MetadataVersionStats metadataVersionStats) {
        writeJsonWithTrailingNewline(
                statsFile,
                normalizeMetadataVersionStats(metadataVersionStats),
                "Failed to write metadata-version stats to "
        );
    }

    public static LibraryStatsModels.LibraryStats loadRepositoryStats(Path statsRoot) {
        LibraryStatsModels.LibraryStats libraryStats = emptyLibraryStats();
        if (!Files.isDirectory(statsRoot)) {
            return libraryStats;
        }

        try (Stream<Path> files = Files.walk(statsRoot)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(path -> isExplodedStatsFile(statsRoot, path))
                    .sorted(Comparator.comparing(path -> path.toAbsolutePath().toString()))
                    .toList()) {
                Path relative = statsRoot.relativize(file);
                String groupId = relative.getName(0).toString();
                String artifactId = relative.getName(1).toString();
                String metadataVersion = relative.getName(2).toString();
                libraryStats = withMetadataVersionStats(
                        libraryStats,
                        groupId + ":" + artifactId,
                        metadataVersion,
                        loadMetadataVersionStats(file)
                );
            }
        } catch (IOException e) {
            throw new GradleException("Failed to traverse repository stats root " + statsRoot, e);
        }
        return libraryStats;
    }

    public static List<LibraryStatsModels.CoordinateMetric> topCoordinatesByMetric(
            LibraryStatsModels.LibraryStats libraryStats,
            String metric,
            int limit
    ) {
        if (limit < 1) {
            throw new GradleException("Top coordinate limit must be positive. Got: " + limit);
        }
        if (!DYNAMIC_ACCESSES_METRIC.equals(metric)) {
            throw new GradleException("Unsupported library stats metric '" + metric + "'. Supported metrics: " + DYNAMIC_ACCESSES_METRIC);
        }

        Map<String, Long> metricByCoordinate = new HashMap<>();
        LibraryStatsModels.LibraryStats normalizedStats = normalizeLibraryStats(libraryStats);
        for (Map.Entry<String, LibraryStatsModels.ArtifactStats> artifactEntry : normalizedStats.entries().entrySet()) {
            String artifact = artifactEntry.getKey();
            LibraryStatsModels.ArtifactStats artifactStats = artifactEntry.getValue();
            for (LibraryStatsModels.MetadataVersionStats metadataVersionStats : artifactStats.metadataVersions().values()) {
                for (LibraryStatsModels.VersionStats versionStats : metadataVersionStats.versions()) {
                    if (versionStats.dynamicAccess() == null || !versionStats.dynamicAccess().isAvailable()) {
                        continue;
                    }
                    String coordinate = artifact + ":" + versionStats.version();
                    metricByCoordinate.merge(coordinate, versionStats.dynamicAccess().totalCalls(), Math::max);
                }
            }
        }

        return metricByCoordinate.entrySet().stream()
                .map(entry -> new LibraryStatsModels.CoordinateMetric(entry.getKey(), entry.getValue()))
                .sorted(Comparator
                        .comparingLong(LibraryStatsModels.CoordinateMetric::value)
                        .reversed()
                        .thenComparing(LibraryStatsModels.CoordinateMetric::coordinate))
                .limit(limit)
                .toList();
    }

    public static void requireCoordinatesInMetadata(Path metadataRoot, List<String> coordinates) {
        List<String> missing = coordinates.stream()
                .filter(coordinate -> !isCoordinateInMetadata(metadataRoot, coordinate))
                .toList();
        if (!missing.isEmpty()) {
            throw new GradleException("Unknown reachability metadata coordinates: " + String.join(", ", missing));
        }
    }

    private static boolean isCoordinateInMetadata(Path metadataRoot, String coordinate) {
        String[] parts = coordinate.split(":", 3);
        if (parts.length != 3) {
            return false;
        }
        String group = parts[0];
        String artifact = parts[1];
        String version = parts[2];
        Path artifactRoot = metadataRoot.resolve(group).resolve(artifact);
        Path indexFile = artifactRoot.resolve("index.json");
        if (!Files.isRegularFile(indexFile)) {
            return false;
        }
        try {
            JsonNode index = OBJECT_MAPPER.readTree(indexFile.toFile());
            if (!index.isArray()) {
                return false;
            }
            for (JsonNode entry : index) {
                JsonNode testedVersions = entry.path("tested-versions");
                String metadataVersion = entry.path("metadata-version").asText("");
                if (testedVersions.isArray()
                        && containsTextValue(testedVersions, version)
                        && Files.isDirectory(artifactRoot.resolve(metadataVersion))) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            throw new GradleException("Failed to read metadata index " + indexFile, e);
        }
    }

    private static boolean containsTextValue(JsonNode values, String expected) {
        for (JsonNode value : values) {
            if (expected.equals(value.asText())) {
                return true;
            }
        }
        return false;
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
        return buildVersionStats(coordinate, libraryJars, dynamicAccessDir, jacocoReport, Set.of());
    }

    /// §AR-test-harness.8: `agentCoveredCallSites` (from `parseAgentOrigins`) enables the
    /// agent-origin fallback for line-less call sites; pass `Set.of()` for the line-based path.
    public static LibraryStatsModels.VersionStats buildVersionStats(
            String coordinate,
            List<Path> libraryJars,
            Path dynamicAccessDir,
            Path jacocoReport,
            Set<AgentCoveredCallSite> agentCoveredCallSites
    ) {
        Set<String> libraryClasses = loadLibraryClasses(libraryJars);
        if (libraryClasses.isEmpty()) {
            return new LibraryStatsModels.VersionStats(
                    versionFromCoordinate(coordinate),
                    LibraryStatsModels.DynamicAccessStatsValue.available(emptyDynamicAccessStats()),
                    unavailableLibraryCoverage()
            );
        }
        JacocoReportParser.ParsedJacocoReport parsedJacocoReport = JacocoReportParser.parse(jacocoReport);
        DynamicAccessReportsParser.ParsedDynamicAccess parsedDynamicAccess =
                DynamicAccessReportsParser.parse(dynamicAccessDir, libraryClasses, parsedJacocoReport.coveredLinesBySource(), agentCoveredCallSites);
        return versionStats(
                coordinate,
                LibraryStatsModels.DynamicAccessStatsValue.available(parsedDynamicAccess.dynamicAccessStats()),
                parsedJacocoReport
        );
    }

    public static LibraryStatsModels.VersionStats buildVersionStatsWithoutDynamicAccess(
            String coordinate,
            Path jacocoReport
    ) {
        JacocoReportParser.ParsedJacocoReport parsedJacocoReport = JacocoReportParser.parse(jacocoReport);
        return versionStats(
                coordinate,
                LibraryStatsModels.DynamicAccessStatsValue.notAvailable(),
                parsedJacocoReport
        );
    }

    public static ExternalDynamicAccessSummary buildExternalDynamicAccessSummary(List<Path> libraryJars, Path dynamicAccessDir) {
        Set<String> libraryClasses = loadLibraryClasses(libraryJars);
        if (libraryClasses.isEmpty()) {
            return new ExternalDynamicAccessSummary(0L, Map.of());
        }
        DynamicAccessReportsParser.ParsedDynamicAccess parsedDynamicAccess =
                DynamicAccessReportsParser.parse(dynamicAccessDir, libraryClasses, Map.of(), Set.of());
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
        return buildDynamicAccessCoverageReport(coordinate, libraryJars, dynamicAccessDir, jacocoReport, Set.of());
    }

    /// §AR-test-harness.8: `agentCoveredCallSites` (from `parseAgentOrigins`) enables the
    /// agent-origin fallback for line-less call sites; pass `Set.of()` for the line-based path.
    public static LibraryStatsModels.DynamicAccessCoverageReport buildDynamicAccessCoverageReport(
            String coordinate,
            List<Path> libraryJars,
            Path dynamicAccessDir,
            Path jacocoReport,
            Set<AgentCoveredCallSite> agentCoveredCallSites
    ) {
        Set<String> libraryClasses = loadLibraryClasses(libraryJars);
        if (libraryClasses.isEmpty()) {
            return new LibraryStatsModels.DynamicAccessCoverageReport(
                    coordinate,
                    false,
                    new LibraryStatsModels.DynamicAccessCoverageTotals(0L, 0L),
                    List.of()
            );
        }
        JacocoReportParser.ParsedJacocoReport parsedJacocoReport = JacocoReportParser.parse(jacocoReport);
        DynamicAccessReportsParser.ParsedDynamicAccess parsedDynamicAccess =
                DynamicAccessReportsParser.parse(dynamicAccessDir, libraryClasses, parsedJacocoReport.coveredLinesBySource(), agentCoveredCallSites);
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

    /// §AR-test-harness.8: delegates to the agent-origin matcher, which retains the statically
    /// reported call sites whose exact tracked API and caller class/method occur, in that
    /// order, on a configuration-carrying agent origin path.
    public static Set<AgentCoveredCallSite> parseAgentOrigins(Path originsOutput, Path dynamicAccessDir) {
        return AgentOriginsParser.parseAgentOrigins(originsOutput, dynamicAccessDir);
    }

    /// §AR-test-harness.8: the agent-origin fallback is the only-when-lines-are-absent gate.
    /// Returns true iff the dynamic-access reports contain at least one library call site and
    /// every such call site is line-less, so line-based matching can never succeed. Purely
    /// data-driven — no jar bytecode scanning.
    public static boolean lineMatchingImpossible(Path dynamicAccessDir, List<Path> libraryJars) {
        Set<String> libraryClasses = loadLibraryClasses(libraryJars);
        if (libraryClasses.isEmpty()) {
            return false;
        }
        DynamicAccessReportsParser.ParsedDynamicAccess parsed =
                DynamicAccessReportsParser.parse(dynamicAccessDir, libraryClasses, Map.of(), Set.of());
        if (parsed.dynamicAccessStats().totalCalls() == 0L) {
            return false;
        }
        for (LibraryStatsModels.DynamicAccessClassCoverage classCoverage : parsed.classCoverage()) {
            for (LibraryStatsModels.DynamicAccessCallSiteCoverage callSite : classCoverage.callSites()) {
                if (callSite.line() != null) {
                    return false;
                }
            }
        }
        return true;
    }

    private static LibraryStatsModels.VersionStats versionStats(
            String coordinate,
            LibraryStatsModels.DynamicAccessStatsValue dynamicAccess,
            JacocoReportParser.ParsedJacocoReport parsedJacocoReport
    ) {
        return new LibraryStatsModels.VersionStats(
                versionFromCoordinate(coordinate),
                dynamicAccess,
                new LibraryStatsModels.LibraryCoverage(
                        parsedJacocoReport.line(),
                        parsedJacocoReport.instruction(),
                        parsedJacocoReport.method()
                )
        );
    }

    public static LibraryStatsModels.DynamicAccessStats emptyDynamicAccessStats() {
        return new LibraryStatsModels.DynamicAccessStats(
                0L,
                0L,
                fullyCoveredRatio(),
                Map.of()
        );
    }

    public static LibraryStatsModels.LibraryCoverage unavailableLibraryCoverage() {
        return new LibraryStatsModels.LibraryCoverage(
                LibraryStatsModels.CoverageMetricValue.notAvailable(),
                LibraryStatsModels.CoverageMetricValue.notAvailable(),
                LibraryStatsModels.CoverageMetricValue.notAvailable()
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

    public static String toNormalizedPrettyJsonWithTrailingNewline(LibraryStatsModels.MetadataVersionStats metadataVersionStats) {
        try {
            String json = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(normalizeMetadataVersionStats(metadataVersionStats));
            return json.endsWith(System.lineSeparator()) ? json : json + System.lineSeparator();
        } catch (IOException e) {
            throw new GradleException("Failed to serialize normalized metadata-version stats JSON", e);
        }
    }

    public static void writeJson(Path targetFile, Object value) {
        writeJsonWithTrailingNewline(targetFile, value, "Failed to write JSON to ");
    }

    public static Path repositoryStatsFile(Path statsRoot, String groupId, String artifactId, String metadataVersion) {
        return statsRoot.resolve(groupId).resolve(artifactId).resolve(metadataVersion).resolve("stats.json");
    }

    static LibraryStatsModels.LibraryStats emptyLibraryStats() {
        return new LibraryStatsModels.LibraryStats(new TreeMap<>());
    }

    public static LibraryStatsModels.MetadataVersionStats emptyMetadataVersionStats() {
        return new LibraryStatsModels.MetadataVersionStats(List.of());
    }

    private static LibraryStatsModels.LibraryStats normalizeLibraryStats(LibraryStatsModels.LibraryStats libraryStats) {
        return LibraryStatsNormalizer.normalizeLibraryStats(libraryStats);
    }

    public static LibraryStatsModels.MetadataVersionStats normalizeMetadataVersionStats(
            LibraryStatsModels.MetadataVersionStats metadataVersionStats
    ) {
        return LibraryStatsNormalizer.normalizeMetadataVersionStats(metadataVersionStats);
    }

    private static boolean isExplodedStatsFile(Path statsRoot, Path file) {
        if (!"stats.json".equals(file.getFileName().toString())) {
            return false;
        }
        Path relative = statsRoot.relativize(file);
        return relative.getNameCount() == 4 && "stats.json".equals(relative.getName(3).toString());
    }

    /// Returns true if at least one of the given JARs contains a `.class` file
    /// (excluding `module-info.class`).
    public static boolean containsClassFiles(List<Path> libraryJars) {
        for (Path jarPath : libraryJars) {
            try (JarFile jarFile = new JarFile(jarPath.toFile())) {
                boolean found = jarFile.stream()
                        .map(JarEntry::getName)
                        .filter(name -> name.endsWith(".class"))
                        .anyMatch(name -> !name.equals("module-info.class"));
                if (found) {
                    return true;
                }
            } catch (IOException e) {
                throw new GradleException("Failed to read library JAR " + jarPath, e);
            }
        }
        return false;
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

    static BigDecimal ratio(long covered, long total) {
        if (total == 0L) {
            return fullyCoveredRatio();
        }
        return BigDecimal.valueOf(covered)
                .divide(BigDecimal.valueOf(total), RATIO_SCALE, RoundingMode.HALF_UP);
    }

    static BigDecimal fullyCoveredRatio() {
        return BigDecimal.ONE.setScale(1, RoundingMode.HALF_UP);
    }

    public record AgentCoveredCallSite(String trackedApi, String className, String methodName) {
    }

    public record ExternalDynamicAccessSummary(
            long totalCalls,
            Map<String, LibraryStatsModels.DynamicAccessBreakdown> breakdown
    ) {
    }
}
