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

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.graalvm.internal.tck.Utils.coordinatesMatch;
import static org.graalvm.internal.tck.Utils.extractJsonFile;
import static org.graalvm.internal.tck.Utils.readIndexFile;
import static org.graalvm.internal.tck.Utils.splitCoordinates;

public abstract class TckExtension {
    private static final List<String> REPO_ROOT_FILES = List.of("CONTRIBUTING.md", "metadata");

    public abstract DirectoryProperty getRepoRoot();

    public abstract DirectoryProperty getMetadataRoot();

    public abstract DirectoryProperty getTestRoot();

    public abstract DirectoryProperty getTckRoot();

    public abstract Property<String> getTestedLibraryVersion();

    @Inject
    public abstract ExecOperations getExecOperations();

    public TckExtension(Project project) {
        getRepoRoot().value(project.getObjects().directoryProperty().value(project.getLayout().getProjectDirectory()).map(dir -> {
            Directory current = dir;
            while (!isRootDir(current)) {
                current = current.dir("..");
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

    private static Path toPath(Provider<? extends FileSystemLocation> provider) {
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
    Path getTestDir(String coordinates) {
        List<String> strings = splitCoordinates(coordinates);
        String groupId = strings.get(0);
        String artifactId = strings.get(1);
        String version = strings.get(2);
        Objects.requireNonNull(groupId, "Group ID must be specified");
        Objects.requireNonNull(artifactId, "Artifact ID must be specified");
        Objects.requireNonNull(version, "Version must be specified");

        // First, let's try if we can find test directory from the new `tests/src/index.json` file.
        List<Map<String, ?>> index = (List<Map<String, ?>>) readIndexFile(testRoot());
        for (Map<String, ?> entry : index) {
            boolean found = ((List<Map<String, ?>>) entry.get("libraries")).stream().anyMatch(
                    lib -> coordinatesMatch((String) lib.get("name"), groupId, artifactId) &&
                           ((List<String>) lib.get("versions")).contains(version)
            );
            if (found) {
                return testRoot().resolve((String) entry.get("test-project-path"));
            }
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
            spec.commandLine("git", "diff", "--name-only", "--diff-filter=ACMRT", baseCommit, newCommit);
        });

        String output = baos.toString(StandardCharsets.UTF_8);
        List<String> diffFiles = Arrays.asList(output.split("\\r?\\n"));

        Path workflowsRoot = repoRoot().resolve(".github").resolve("workflows");
        AtomicBoolean testAll = new AtomicBoolean(false);
        // Group files by if they belong to 'metadata' or 'test' directory structures.
        Map<String, List<Path>> changed = diffFiles.stream()
                .map(line -> repoRoot().resolve(line))
                .collect(Collectors.groupingBy((Path path) -> {
                    if (path.startsWith(tckRoot()) || path.startsWith(workflowsRoot)) {
                        testAll.set(true);
                        return "other";
                    } else if (path.startsWith(testRoot())) {
                        return "test";
                    } else if (path.startsWith(metadataRoot())) {
                        return "metadata";
                    } else {
                        return "other";
                    }
                }));

        if (testAll.get()) {
            // If tck was changed we should retest everything, just to be safe.
            return getMatchingCoordinates("");
        }

        // First get all available coordinates, then filter them by if their corresponding metadata / tests directories
        // contain changed files.
        return getMatchingCoordinates("").stream().filter(c -> {
            Path metadataDir = getMetadataDir(c);
            if (changed.get("metadata").stream().anyMatch(f -> f.startsWith(metadataDir))) {
                return true;
            }
            Path testDir = getTestDir(c);
            if (changed.get("test").stream().anyMatch(f -> f.startsWith(testDir))) {
                return true;
            }
            return false;
        }).distinct().collect(Collectors.toList());
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
    Path getMetadataDir(String coordinates) {
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
    List<String> getMatchingCoordinates(String coordinateFilter) {
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
        return matchingCoordinates.stream().collect(Collectors.toList());
    }

    /**
     * Returns a list of metadata files in a given directory.
     *
     * @param directory
     * @return list of json files contained in it
     */
    @SuppressWarnings("unchecked")
    List<String> getMetadataFileList(Path directory) throws IOException {
        List<String> foundFiles = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(s -> s.endsWith(".json") && !s.endsWith("index.json"))
                    .forEach(foundFiles::add);
        }
        Path indexFile = directory.resolve("index.json");
        if (indexFile.toFile().exists()) {
            List<String> indexFiles = (List<String>) extractJsonFile(indexFile);
            if (!new HashSet<>(indexFiles).equals(new HashSet<>(foundFiles))) {
                throw new IllegalStateException("Metadata file list in '" + indexFile.toAbsolutePath() + "' is not up to date!");
            }
            return indexFiles;
        } else {
            return foundFiles;
        }
    }
}
