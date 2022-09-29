/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package org.graalvm.internal.tck.harness

import org.graalvm.internal.common.MetadataDescriptor

import java.nio.file.Path
import java.util.stream.Collectors

import static org.graalvm.internal.tck.RepoScanner.*

/**
 * Class that provides static methods that are used to fetch tests for metadata.
 */
@SuppressWarnings("unused")
class TestLookupLogic {
    /**
     * Returns a list of coordinates that match changed files between baseCommit and newCommit.
     * @param baseCommit
     * @param newCommit
     * @return List of coordinates
     */
    @SuppressWarnings("unused")
    static List<MetadataDescriptor> diffDescriptors(String baseCommit, String newCommit) {
        String cmd = "git diff --name-only --diff-filter=ACMRT ${baseCommit} ${newCommit}"

        Process p = cmd.execute()
        String output = p.in.text
        List<String> diffFiles = Arrays.asList(output.split("\\r?\\n"))

        Path workflowsRoot = repoRoot.resolve(".github").resolve("workflows")
        boolean testAll = false
        // Group files by if they belong to 'metadata' or 'test' directory structures.
        Map<String, List<Path>> changed = diffFiles.stream()
                .map(line -> repoRoot.resolve(line))
                .collect(Collectors.groupingBy(path -> {
                    if (path.startsWith(tckRoot) || path.startsWith(workflowsRoot)) {
                        testAll = true
                        return "other"
                    } else if (path.startsWith(testRoot)) {
                        return "test"
                    } else if (path.startsWith(metadataRoot)) {
                        return "metadata"
                    } else {
                        return "other"
                    }
                }))

        if (testAll) {
            // If tck was changed we should retest everything, just to be safe.
            return MetadataLookupLogic.getAllDescriptors()
        }

        // First get all available tests, then filter them by if their corresponding metadata / tests directories
        // contain changed files.
        return MetadataLookupLogic.getAllDescriptors().stream().filter(d -> {
            Path metadataDir = d.getMetadataDir()
            if (changed["metadata"].stream().anyMatch(f -> f.startsWith(metadataDir))) {
                return true
            }
            Path testDir = d.getTestDir()
            if (changed["test"].stream().anyMatch(f -> f.startsWith(testDir))) {
                return true
            }
            return false
        }).collect(Collectors.toSet()).toList()
    }
}
