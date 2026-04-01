/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness;

import groovy.json.JsonSlurper;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.process.ExecOperations;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.graalvm.internal.tck.Utils.*;

public abstract class TckExtension {
    private static final List<String> REPO_ROOT_FILES = List.of("LICENSE", "metadata", "tests");

    public abstract DirectoryProperty getRepoRoot();

    public abstract DirectoryProperty getMetadataRoot();

    public abstract DirectoryProperty getTestRoot();

    public abstract DirectoryProperty getTckRoot();

    public abstract Property<@NotNull String> getTestedLibraryVersion();

    @Inject
    public abstract ExecOperations getExecOperations();

    public TckExtension(Project project) {
        getRepoRoot().value(project.getObjects().directoryProperty().value(project.getLayout().getProjectDirectory()).map(dir -> {
            Directory current = dir;
            var level = 0;
            int maxLevel = 50;
            while (!isRootDir(current)) {
                current = current.dir("..");
                if (level++ == maxLevel) {
                    throw new RuntimeException("Could not detect root dir at level " + maxLevel);
                }
            }
            return current;
        })).finalizeValueOnRead();
        getMetadataRoot().value(getRepoRoot().dir("metadata")).finalizeValueOnRead();
        getTestRoot().value(getRepoRoot().dir("tests/src")).finalizeValueOnRead();
        getTckRoot().value(getRepoRoot().dir("tests/tck-build-logic")).finalizeValueOnRead();
    }

    private static boolean isRootDir(Directory dir) {
        return REPO_ROOT_FILES.stream().allMatch(fileName -> dir.file(fileName).getAsFile().exists());
    }

    private static Path toPath(Provider<? extends @NotNull FileSystemLocation> provider) {
        return provider.get().getAsFile().toPath();
    }

    private Path repoRoot() {
        return toPath(getRepoRoot());
    }

    private Path metadataRoot() {
        return toPath(getMetadataRoot());
    }

    private Path tckRoot() {
        return toPath(getTckRoot());
    }

    private Path testRoot() {
        return toPath(getTestRoot());
    }

    /**
     * Given full coordinates returns matching test directory
     */
    @SuppressWarnings("unchecked")
    public Path getTestDir(String coordinates) {
        List<String> strings = splitCoordinates(coordinates);
        String groupId = strings.get(0);
        String artifactId = strings.get(1);
        String version = strings.get(2);
        Objects.requireNonNull(groupId, "Group ID must be specified");
        Objects.requireNonNull(artifactId, "Artifact ID must be specified");
        Objects.requireNonNull(version, "Version must be specified");

        // First, try to locate the test project via the metadata/<group>/<artifact>/index.json.
        try {
            Path metadataDir = metadataRoot()
                    .resolve(groupId)
                    .resolve(artifactId);

            if (Files.exists(metadataDir)) {
                List<Map<String, ?>> metadataIndex = (List<Map<String, ?>>) readIndexFile(metadataDir);

                // Resolve the entry that declares support for the requested library version.
                Optional<Map<String, ?>> matchingEntry = metadataIndex.stream()
                        .filter(entry -> {
                            Object testedVersions = entry.get("tested-versions");
                            return testedVersions instanceof List<?> versions && versions.contains(version);
                        })
                        .findFirst();

                if (matchingEntry.isPresent()) {
                    Map<String, ?> entry = matchingEntry.get();

                    // Determine the test version to use: 'test-version' if present, otherwise 'metadata-version'
                    String testVersion;
                    if (entry.containsKey("test-version")) {
                        testVersion = (String) entry.get("test-version");
                    } else {
                        testVersion = (String) entry.get("metadata-version");
                    }

                    Path indexedTest = testRoot().resolve(groupId).resolve(artifactId).resolve(testVersion);

                    if (Files.isDirectory(indexedTest)) {
                        return indexedTest;
                    }
                    // Error: The index pointed to a specific test directory that is missing.
                    throw new RuntimeException("Test directory specified in index.json (`" + indexedTest + "`) is missing for coordinates: " + coordinates);
                }
            }
        } catch (Exception ignored) {
            // Fall through to conventional lookup
        }

        // Fallback: conventional layout tests/src/<group>/<artifact>/<version>
        Path conventional = testRoot().resolve(groupId).resolve(artifactId).resolve(version);
        if (Files.isDirectory(conventional)) {
            return conventional;
        }

        throw new RuntimeException("Missing test-directory for coordinates `" + coordinates + "`");
    }

    /**
     * Returns a list of coordinates that match changed files between baseCommit and newCommit.
     *
     * @return List of coordinates
     */
    @SuppressWarnings("unused")
    List<String> diffCoordinates(String baseCommit, String newCommit) {
        Path workflowsRoot = repoRoot().resolve(".github").resolve("workflows");
        Map<String, List<Path>> changed = diffFiles(baseCommit, newCommit, "ACMRTD").stream()
                .collect(Collectors.groupingBy((Path path) -> {
                    if (path.startsWith(tckRoot()) || path.startsWith(workflowsRoot)) {
                        return "logic";
                    } else if (path.startsWith(testRoot())) {
                        return "test";
                    } else if (path.startsWith(metadataRoot())) {
                        return "metadata";
                    } else {
                        return "other";
                    }
                }));

        List<Path> changedMetadataFiles = changed.getOrDefault("metadata", Collections.emptyList());
        List<Path> changedTestFiles = changed.getOrDefault("test", Collections.emptyList());
        List<Path> changedLogicFiles = changed.getOrDefault("logic", Collections.emptyList());

        // if we didn't change any of metadata, tests or logic we don't need to test anything
        if (changedMetadataFiles.isEmpty() && changedTestFiles.isEmpty() && changedLogicFiles.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> changedCoordinates = getMatchingCoordinatesStrict("").stream().filter(c -> {
            Path metadataDir = getMetadataDir(c);
            if (changedMetadataFiles.stream().anyMatch(f -> f.startsWith(metadataDir))) {
                return true;
            }
            Path testDir = getTestDir(c);
            return changedTestFiles.stream().anyMatch(f -> f.startsWith(testDir));
        }).distinct().collect(Collectors.toCollection(ArrayList::new));

        return changedCoordinates;
    }

    /**
     * Returns a list of changed artifact coordinates based on artifact-level index.json files modified between baseCommit and newCommit.
     *
     * @return List of coordinates (e.g., "org.flywaydb:flyway-core" or "org.example:library")
     */
    public List<String> diffIndexCoordinates(String baseCommit, String newCommit) {
        return diffFiles(baseCommit, newCommit, "ACMRT").stream()
                .map(path -> repoRoot().relativize(path).toString().replace('\\', '/'))
                .map(this::indexPathToCoordinate)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Returns changed artifact coordinates paired with only newly added tested-versions from artifact-level index.json diffs.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> diffIndexTestedVersions(String baseCommit, String newCommit) {
        return diffFiles(baseCommit, newCommit, "ACMRT").stream()
                .map(path -> repoRoot().relativize(path).toString().replace('\\', '/'))
                .map(relativePath -> diffIndexTestedVersionsEntry(baseCommit, newCommit, relativePath))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<Path> diffFiles(String baseCommit, String newCommit, String diffFilter) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        getExecOperations().exec(spec -> {
            spec.setStandardOutput(baos);
            spec.commandLine("git", "diff", "--name-only", "--diff-filter=" + diffFilter, baseCommit, newCommit);
        });

        return Arrays.stream(baos.toString(StandardCharsets.UTF_8).split("\\r?\\n"))
                .filter(line -> !line.isBlank())
                .map(line -> repoRoot().resolve(line))
                .collect(Collectors.toList());
    }

    /**
     * Maps an artifact-level metadata index.json file path to its corresponding coordinate string.
     *
     * @return the coordinate string (G:A), or null if not a match
     */
    private String indexPathToCoordinate(String path) {
        Pattern pattern = Pattern.compile("(?:metadata|tests/src)/([^/]+)/([^/]+)(?:/([^/]+))?/index\\.json");
        Matcher matcher = pattern.matcher(path);

        if (matcher.matches()) {
            String group = matcher.group(1);
            String artifact = matcher.group(2);
            String version = matcher.group(3);

            return (version != null)
                    ? String.format("%s:%s:%s", group, artifact, version)
                    : String.format("%s:%s", group, artifact);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> diffIndexTestedVersionsEntry(String baseCommit, String newCommit, String relativePath) {
        String coordinates = indexPathToCoordinate(relativePath);
        if (coordinates == null) {
            return null;
        }

        List<Map<String, ?>> baseEntries = readJsonListFromCommit(baseCommit, relativePath);
        List<Map<String, ?>> headEntries = readJsonListFromCommit(newCommit, relativePath);

        Map<String, Set<String>> baseByMetadataVersion = testedVersionsByMetadataVersion(baseEntries);
        Map<String, Set<String>> headByMetadataVersion = testedVersionsByMetadataVersion(headEntries);

        Set<String> addedVersions = new LinkedHashSet<>();
        for (Map.Entry<String, Set<String>> headEntry : headByMetadataVersion.entrySet()) {
            Set<String> baseVersions = baseByMetadataVersion.getOrDefault(headEntry.getKey(), Collections.emptySet());
            for (String testedVersion : headEntry.getValue()) {
                if (!baseVersions.contains(testedVersion)) {
                    addedVersions.add(testedVersion);
                }
            }
        }

        if (addedVersions.isEmpty()) {
            return null;
        }

        return Map.of(
                "coordinates", coordinates,
                "versions", new ArrayList<>(addedVersions)
        );
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, ?>> readJsonListFromCommit(String commit, String relativePath) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        var result = getExecOperations().exec(spec -> {
            spec.setIgnoreExitValue(true);
            spec.setStandardOutput(stdout);
            spec.setErrorOutput(stderr);
            spec.commandLine("git", "show", commit + ":" + relativePath);
        });
        if (result.getExitValue() != 0) {
            return Collections.emptyList();
        }
        String json = stdout.toString(StandardCharsets.UTF_8);
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        Object parsed = parseTextJson(json);
        if (parsed instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(entry -> (Map<String, ?>) entry)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Set<String>> testedVersionsByMetadataVersion(List<Map<String, ?>> entries) {
        Map<String, Set<String>> testedVersions = new LinkedHashMap<>();
        for (Map<String, ?> entry : entries) {
            if (entry == null) {
                continue;
            }
            Object metadataVersion = entry.get("metadata-version");
            Object testedVersionsRaw = entry.get("tested-versions");
            if (!(metadataVersion instanceof String metadataVersionString) || !(testedVersionsRaw instanceof List<?> versions)) {
                continue;
            }
            testedVersions.put(
                    metadataVersionString,
                    versions.stream()
                            .map(Object::toString)
                            .collect(Collectors.toCollection(LinkedHashSet::new))
            );
        }
        return testedVersions;
    }

    private Object parseTextJson(String json) {
        try {
            return new JsonSlurper().parseText(json);
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    /**
     * Returns a set of all directories that match given group ID and artifact ID.
     * null values match every possible value (null artifact ID matches all artifacts in given group).
     *
     * @return set of all directories that match given criteria
     */
    Set<String> getMatchingMetadataDirs(String groupId, String artifactId) {
        Set<String> dirs = new LinkedHashSet<>();
        Path root = metadataRoot();

        try (Stream<Path> groupDirs = Files.list(root)) {
            groupDirs.filter(Files::isDirectory).forEach(g -> {
                String gName = g.getFileName().toString();
                // Filter by group if provided
                if (groupId != null && !groupId.equals(gName)) return;

                try (Stream<Path> artifactDirs = Files.list(g)) {
                    artifactDirs.filter(Files::isDirectory).forEach(a -> {
                        String aName = a.getFileName().toString();
                        // Filter by artifact if provided
                        if (artifactId != null && !artifactId.equals(aName)) return;

                        Path indexPath = a.resolve("index.json");
                        if (Files.isRegularFile(indexPath)) {
                            // Add this artifact directory
                            dirs.add(a.toAbsolutePath().toString());
                        }
                    });
                } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}

        return dirs;
    }

    /**
     * Returns metadata directory for given full coordinates
     *
     * @return path to metadata directory
     */
    @SuppressWarnings("unchecked")
    public Path getMetadataDir(String coordinates) {
        List<String> strings = splitCoordinates(coordinates);
        String groupId = strings.get(0);
        String artifactId = strings.get(1);
        String version = strings.get(2);
        Objects.requireNonNull(groupId, "Group ID must be specified");
        Objects.requireNonNull(artifactId, "Artifact ID must be specified");
        Objects.requireNonNull(version, "Version must be specified");

        // Resolve directly to metadata/<groupId>/<artifactId>/index.json without expanding "requires"
        Path artifactDir = metadataRoot().resolve(groupId).resolve(artifactId);
        Path indexPath = artifactDir.resolve("index.json");
        if (!Files.isRegularFile(indexPath)) {
            throw new RuntimeException("Missing index.json for " + groupId + ":" + artifactId + " at " + indexPath);
        }

        List<Map<String, ?>> metadataIndex = (List<Map<String, ?>>) extractJsonFile(indexPath);
        for (Map<String, ?> entry : metadataIndex) {
            @SuppressWarnings("unchecked")
            List<String> tv = (List<String>) entry.get("tested-versions");
            if (tv != null && tv.contains(version)) {
                String metaVersion = (String) entry.get("metadata-version");
                Path result = artifactDir.resolve(metaVersion);
                if (Files.isDirectory(result)) {
                    return result;
                }
                throw new RuntimeException("Index.json for " + groupId + ":" + artifactId + " maps version " + version + " to missing dir " + result);
            }
        }
        throw new RuntimeException("Missing metadata for " + coordinates);
    }

    /**
     * Returns all coordinates that match given coordinate filter.
     * Standard version: Uses 'metadata-version' pointer for shared config folders.
     *
     * @return list of all coordinates that match given coordinate filter.
     */
    @SuppressWarnings("unchecked")
    public List<String> getMatchingCoordinates(String coordinateFilter) {
        List<String> parts = splitCoordinates(coordinateFilter);
        String versionFilter = parts.get(2);

        Set<String> results = new HashSet<>();
        loadMatchingIndices(parts.get(0), parts.get(1)).forEach((fullDir, index) -> {
            String g = fullDir.getParent().getFileName().toString();
            String a = fullDir.getFileName().toString();

            for (Map<String, ?> entry : index) {
                List<String> tested = (List<String>) entry.get("tested-versions");
                String metaVer = (String) entry.get("metadata-version");

                if (tested == null || metaVer == null || !Files.isDirectory(fullDir.resolve(metaVer))) {
                    continue;
                }

                tested.stream()
                        .filter(v -> versionFilter == null || versionFilter.equals(v))
                        .forEach(v -> results.add(g + ":" + a + ":" + v));
            }
        });
        return new ArrayList<>(results);
    }

    /**
     * Returns all coordinates that match given coordinate filter.
     * Strict version: Requires library version string to match directory name exactly.
     *
     * @return list of all coordinates that match given coordinate filter.
     */
    @SuppressWarnings("unchecked")
    public List<String> getMatchingCoordinatesStrict(String coordinateFilter) {
        List<String> parts = splitCoordinates(coordinateFilter);
        String versionFilter = parts.get(2);

        Set<String> results = new HashSet<>();
        loadMatchingIndices(parts.get(0), parts.get(1)).forEach((fullDir, index) -> {
            String g = fullDir.getParent().getFileName().toString();
            String a = fullDir.getFileName().toString();

            for (Map<String, ?> entry : index) {
                List<String> tested = (List<String>) entry.get("tested-versions");
                if (tested == null) continue;

                tested.stream()
                        .filter(v -> versionFilter == null || versionFilter.equals(v))
                        .filter(v -> Files.isDirectory(fullDir.resolve(v)))
                        .forEach(v -> results.add(g + ":" + a + ":" + v));
            }
        });
        return new ArrayList<>(results);
    }

    /**
     * Internal helper to load and group index.json files for matching directories.
     */
    private Map<Path, List<Map<String, ?>>> loadMatchingIndices(String groupId, String artifactId) {
        Map<Path, List<Map<String, ?>>> indices = new HashMap<>();
        for (String directory : getMatchingMetadataDirs(groupId, artifactId)) {
            Path fullDir = metadataRoot().resolve(directory);
            Path indexFile = fullDir.resolve("index.json");

            @SuppressWarnings("unchecked")
            List<Map<String, ?>> metadataIndex = (List<Map<String, ?>>) extractJsonFile(indexFile);
            if (metadataIndex != null) {
                indices.put(fullDir, metadataIndex);
            }
        }
        return indices;
    }

    /**
     * Returns a list of metadata files in a given directory.
     *
     * @return list of json files contained in it
     */
    public List<String> getMetadataFileList(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.endsWith(".json"))
                    .collect(Collectors.toList());
        }
    }
}
