/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package org.graalvm.internal.tck


import org.gradle.api.Project

import java.nio.file.Path

@SuppressWarnings("unused")
class RepoScanner {
    public static Path repoRoot
    public static Path metadataRoot
    public static Path testRoot
    public static Path tckRoot

    public static String testedLibraryVersion

    static final ArrayList<String> REPO_ROOT_FILES = ["CONTRIBUTING.md", "metadata"]

    /**
     * Method that determines relevant directories from given project and initializes relevant properties.
     * @param project
     */
    static void locateRepoDirs(Project project) {
        repoRoot = project.rootDir.toPath()

        while (!REPO_ROOT_FILES.stream().allMatch(fileName -> repoRoot.resolve(fileName).toFile().exists())) {
            repoRoot = repoRoot.getParent()
        }

        metadataRoot = repoRoot.resolve("metadata")
        testRoot = repoRoot.resolve("tests").resolve("src")
        tckRoot = repoRoot.resolve("tests").resolve("tck-build-logic")
    }
}
