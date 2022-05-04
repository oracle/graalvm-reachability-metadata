/*
 * Licensed under Public Domain (CC0)
 *
 * To the extent possible under law, the person who associated CC0 with
 * this code has waived all copyright and related or neighboring
 * rights to this code.
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package org.graalvm.internal.tck.harness

import java.nio.file.Path
import java.util.stream.Collectors

import static org.graalvm.internal.tck.TestUtils.metadataRoot
import static org.graalvm.internal.tck.TestUtils.repoRoot
import static org.graalvm.internal.tck.TestUtils.testRoot
import static org.graalvm.internal.tck.Utils.coordinatesMatch
import static org.graalvm.internal.tck.Utils.readIndexFile
import static org.graalvm.internal.tck.Utils.splitCoordinates
import static org.graalvm.internal.tck.harness.MetadataLookupLogic.getMetadataDir

/**
 * Class that provides static methods that are used to fetch tests for metadata.
 */
@SuppressWarnings("unused")
class TestLookupLogic {
    /**
     * Given full coordinates returns matching test directory
     * @param coordinates
     * @return
     */
    static Path getTestDir(String coordinates) {
        def (String groupId, String artifactId, String version) = splitCoordinates(coordinates)
        Objects.requireNonNull(groupId, "Group ID must be specified")
        Objects.requireNonNull(artifactId, "Artifact ID must be specified")
        Objects.requireNonNull(version, "Version must be specified")

        // First, let's try if we can find test directory from the new `tests/src/index.json` file.

        List<Map<String, ?>> index = readIndexFile(testRoot) as List<Map<String, ?>>
        for (Map<String, ?> entry in index) {
            boolean found = ((List<Map<String, ?>>) entry.get("libraries")).stream().anyMatch(
                    lib -> {
                        return coordinatesMatch((String) lib.get("name"), groupId, artifactId) &&
                                ((List<String>) lib.get("versions")).contains(version)
                    }
            )
            if (found) {
                return testRoot.resolve((String) entry.get("test-project-path"))
            }
        }

        // If that failed let's try looking up the old way of storing test executions (as a field in metadata index).
        // TODO: Remove this when all PR's are merged and updated to new format.
        index = readIndexFile(getMetadataDir(coordinates).parent) as List<Map<String, ?>>
        def result = index.stream()
                .filter(l -> l.containsKey("test-directory") && coordinatesMatch((String) l.get("module"), groupId, artifactId) && ((ArrayList<String>) l.get("tested-versions")).contains(version))
                .map(l -> l.get("test-directory"))
                .findFirst()

        if (!result.present) {
            throw new RuntimeException("Missing test-directory for coordinates `${coordinates}`")
        }
        return testRoot.resolve((String) result.get())
    }

    /**
     * Returns a list of coordinates that match changed files between baseCommit and newCommit.
     * @param baseCommit
     * @param newCommit
     * @return List of coordinates
     */
    @SuppressWarnings("unused")
    static List<String> diffCoordinates(String baseCommit, String newCommit) {
        String cmd = "git diff --name-only --diff-filter=ACMRT ${baseCommit} ${newCommit}"

        Process p = cmd.execute()
        String output = p.in.text
        List<String> diffFiles = Arrays.asList(output.split("\\r?\\n"))

        // Group files by if they belong to 'metadata' or 'test' directory structures.
        Map<String, List<Path>> changed = diffFiles.stream()
                .map(line -> repoRoot.resolve(line))
                .collect(Collectors.groupingBy(path -> {
                    if (path.startsWith(testRoot)) {
                        return "test"
                    } else if (path.startsWith(metadataRoot)) {
                        return "metadata"
                    } else {
                        return "other"
                    }
                }))

        // First get all available coordinates, then filter them by if their corresponding metadata / tests directories
        // contain changed files.
        return MetadataLookupLogic.getMatchingCoordinates("").stream().filter(c -> {
            Path metadataDir = getMetadataDir(c)
            if (changed["metadata"].stream().anyMatch(f -> f.startsWith(metadataDir))) {
                return true
            }
            Path testDir = getTestDir(c)
            if (changed["test"].stream().anyMatch(f -> f.startsWith(testDir))) {
                return true
            }
            return false
        }).collect(Collectors.toSet()).toList()
    }
}
