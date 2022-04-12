package org.graalvm.internal.tck

import org.gradle.api.Project

import java.nio.file.Path

@SuppressWarnings("unused")
class TestUtils {
    public static Path repoRoot
    public static Path metadataRoot
    public static Path testRoot
    public static Path tckRoot

    public static String testedLibraryVersion
    public static Path metadataDir

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
