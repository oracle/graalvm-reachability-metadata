/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness;

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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
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

                // Find the entry where 'metadata-version' EXACTLY matches the input library version
                Optional<Map<String, ?>> matchingEntry = metadataIndex.stream()
                        .filter(entry -> version.equals(entry.get("metadata-version")))
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
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        getExecOperations().exec(spec -> {
            spec.setStandardOutput(baos);
            spec.commandLine("git", "diff", "--name-only", "--diff-filter=ACMRTD", baseCommit, newCommit);
        });

        String output = baos.toString(StandardCharsets.UTF_8);
        List<String> diffFiles = Arrays.asList(output.split("\\r?\\n"));

        Path workflowsRoot = repoRoot().resolve(".github").resolve("workflows");
        // Group files by if they belong to 'metadata' or 'test' directory structures.
        Map<String, List<Path>> changed = diffFiles.stream()
                .map(line -> repoRoot().resolve(line))
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

        // if we didn't change any of metadata, tests or logic we don't need to test anything
        if (changed.get("metadata") != null && changed.get("metadata").isEmpty()
                && changed.get("test") != null && changed.get("test").isEmpty()
                && changed.get("logic") != null && changed.get("logic").isEmpty()) {
            return new ArrayList<>();
        }

        // First get all available coordinates, then filter them by if their corresponding metadata / tests directories
        // contain changed files.
        List<String> changedCoordinates = getMatchingCoordinates("").stream().filter(c -> {
            Path metadataDir = getMetadataDir(c);
            if (changed.get("metadata") != null && changed.get("metadata").stream().anyMatch(f -> f.startsWith(metadataDir))) {
                return true;
            }
            Path testDir = getTestDir(c);
            return changed.get("test") != null &&
                    changed.get("test").stream().anyMatch(f -> f.startsWith(testDir));
        }).distinct().collect(Collectors.toList());

        // if we detected changes in repo, but not in metadata/index.json file, we should throw an exception
        Set<String> metadataIndexEntries = getMatchingMetadataDirs(null, null);
        List<String> changedEntries = new ArrayList<>();
        if (changed.get("metadata") != null) {
            changedEntries = changed.get("metadata")
                    .stream()
                    .map(m -> m.toString().split("reachability-metadata/metadata/")[1])
                    .filter(m -> !m.equalsIgnoreCase("index.json"))
                    .toList();
        }
        if (!metadataIndexContainsChangedEntries(metadataIndexEntries, changedEntries)) {
            URI metadataRootIndex = Paths.get(metadataRoot() + "/index.json").toUri();
            throw new IllegalStateException("Changes detected but no corresponding entries found in " + metadataRootIndex +
                    ". Please, check whether you added new entries in index file or not.");
        }

        return changedCoordinates;
    }

    /**
     * Returns a list of changed index.json files between baseCommit and newCommit.
     *
     * @return List of index.json files
     */
    public List<String> diffIndexFiles(String baseCommit, String newCommit) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        getExecOperations().exec(spec -> {
            spec.setStandardOutput(baos);
            spec.commandLine("git", "diff", "--name-only", "--diff-filter=ACMRT",
                    baseCommit, newCommit);
        });

        String output = baos.toString(StandardCharsets.UTF_8);
        List<String> diffFiles = Arrays.asList(output.split("\\r?\\n"));

        return diffFiles.stream()
                .filter(f -> f.endsWith("index.json"))
                .distinct()
                .collect(Collectors.toList());
    }

    private boolean metadataIndexContainsChangedEntries(Set<String> changedCoordinates, List<String> changedEntries) {
        boolean containsAll = true;
        for (var n : changedEntries) {
            boolean containsCurrent = false;
            for (var c : changedCoordinates) {
                if (n.startsWith(c.replace(":", "/"))) {
                    containsCurrent = true;
                    break;
                }
            }

            containsAll = containsAll && containsCurrent;
        }

        return containsAll;
    }

    /**
     * Returns a set of all directories that match given group ID and artifact ID.
     * null values match every possible value (null artifact ID matches all artifacts in given group).
     *
     * @return set of all directories that match given criteria
     */
    @SuppressWarnings("unchecked")
    Set<String> getMatchingMetadataDirs(String groupId, String artifactId) {
        Set<String> dirs = new HashSet<>();
        List<Map<String, ?>> index = (List<Map<String, ?>>) readIndexFile(metadataRoot());
        for (Map<String, ?> library : index) {
            if (coordinatesMatch((String) library.get("module"), groupId, artifactId)) {
                if (library.containsKey("directory")) {
                    dirs.add((String) library.get("directory"));
                }
                if (library.containsKey("requires")) {
                    for (String dep : (Collection<String>) library.get("requires")) {
                        List<String> strings = splitCoordinates(dep);
                        String depGroup = strings.get(0);
                        String depArtifact = strings.get(1);
                        dirs.addAll(getMatchingMetadataDirs(depGroup, depArtifact));
                    }
                }
            }
        }

        if (groupId != null && artifactId != null) {
            // Let's see if library wasn't added to index but is present on the disk.
            Path defaultDir = metadataRoot().resolve(groupId).resolve(artifactId);
            if (defaultDir.resolve("index.json").toFile().exists()) {
                dirs.add(defaultDir.toString());
            }
        }
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

        Set<String> matchingDirs = getMatchingMetadataDirs(groupId, artifactId);
        for (String directory : matchingDirs) {
            Path fullDir = metadataRoot().resolve(directory);
            Path index = fullDir.resolve("index.json");
            List<Map<String, ?>> metadataIndex = (List<Map<String, ?>>) extractJsonFile(index);

            for (Map<String, ?> entry : metadataIndex) {
                if (coordinatesMatch((String) entry.get("module"), groupId, artifactId) && ((List<String>) entry.get("tested-versions")).contains(version)) {
                    return fullDir.resolve((String) entry.get("metadata-version"));
                }
            }
        }
        throw new RuntimeException("Missing metadata for " + coordinates);
    }

    /**
     * Returns all coordinates that match given coordinate filter.
     *
     * @return list of all coordinates that
     */
    @SuppressWarnings("unchecked")
    public List<String> getMatchingCoordinates(String coordinateFilter) {
        List<String> strings = splitCoordinates(coordinateFilter);
        String groupId = strings.get(0);
        String artifactId = strings.get(1);
        String version = strings.get(2);

        Set<String> matchingCoordinates = new HashSet<>();
        for (String directory : getMatchingMetadataDirs(groupId, artifactId)) {
            Path index = metadataRoot().resolve(directory).resolve("index.json");
            List<Map<String, ?>> metadataIndex = (List<Map<String, ?>>) extractJsonFile(index);

            for (Map<String, ?> entry : metadataIndex) {
                List<String> coordinates = splitCoordinates((String) entry.get("module"));
                List<String> testedVersions = (List<String>) entry.get("tested-versions");
                if (coordinatesMatch((String) entry.get("module"), groupId, artifactId) && (version == null || testedVersions.contains(version))) {
                    if (version == null) { // We want all library versions, so let's add them.
                        testedVersions.stream()
                                .filter(t -> metadataRoot().resolve(coordinates.get(0)).resolve(coordinates.get(1)).resolve(t).toFile().exists())
                                .forEach(t -> matchingCoordinates.add(entry.get("module") + ":" + t));
                    } else { // We have a specific version pinned.
                        if (metadataRoot().resolve(coordinates.get(0)).resolve(coordinates.get(1)).resolve(version).toFile().exists()) {
                            matchingCoordinates.add(entry.get("module") + ":" + version);
                        }
                    }
                }
            }
        }

        return new ArrayList<>(matchingCoordinates);
    }

    /**
     * Returns a list of metadata files in a given directory.
     *
     * @return list of json files contained in it
     */
    @SuppressWarnings("unchecked")
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
