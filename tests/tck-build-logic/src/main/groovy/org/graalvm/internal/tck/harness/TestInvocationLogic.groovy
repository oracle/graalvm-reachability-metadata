package org.graalvm.internal.tck.harness

import groovy.json.JsonSlurper

import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import java.util.stream.Stream

import static org.graalvm.internal.tck.TestUtils.repoRoot
import static org.graalvm.internal.tck.TestUtils.metadataRoot
import static org.graalvm.internal.tck.TestUtils.testRoot

class TestInvocationLogic {
    /**
     * Given path to a JSON file return its parsed content
     *
     * @param jsonPath path to a JSON file
     * @return parsed contents
     */
    static Object extractJsonFile(Path jsonPath) {
        return new JsonSlurper().parseText(jsonPath.toFile().text)
    }

    /**
     * Splits maven coordinates into group ID, artifact ID and version.
     * If some part of coordinate is missing, return null instead.
     *
     * @param coordinates maven coordinate
     * @return list containing groupId, artifactId and version (or null values if missing)
     */
    static List<String> splitCoordinates(String coordinates) {
        List<String> parts = coordinates.split(':').toList()
        parts.addAll((List<String>) [null] * 3) // Maven coordinates consist of 3 parts

        if (parts[0] == "" || parts[0] == "all" || parts[0] == "any") {
            parts[0] = null
        }
        return parts[0..2]
    }

    /**
     * Checks if given coordinates string matches given group ID and artifact ID.
     * null values match every possible value (null artifact ID matches all artifacts in given group).
     *
     * @param coordinates maven coordinate
     * @param groupId group ID to match
     * @param artifactId artifact ID to match
     * @return boolean if coordinates matches said group ID / artifact ID combo
     */
    static boolean coordinatesMatch(String coordinates, String groupId, String artifactId) {
        def (String testGroup, String testArtifact) = splitCoordinates(coordinates)
        if (groupId != testGroup && groupId != null) {
            return false
        }
        if (artifactId != testArtifact && artifactId != null) {
            return false
        }
        return true
    }

    /**
     * Returns all libraries that are specified in index.json file from metadata directory.
     *
     * @return list of all library definitions
     */
    static List<Map<String, ?>> getAllLibrariesIndex(Path metadataDir) {
        return (List<Map<String, ?>>) extractJsonFile(metadataDir.resolve('index.json'))
    }

    /**
     * Returns a set of all directories that match given group ID and artifact ID.
     * null values match every possible value (null artifact ID matches all artifacts in given group).
     *
     * @return set of all directories that match given criteria
     */
    static Set<String> getMatchingDirs(String groupId, String artifactId) {
        Set<String> dirs = new HashSet<String>()
        for (Map<String, ?> library in getAllLibrariesIndex(metadataRoot)) {
            if (coordinatesMatch((String) library["module"], groupId, artifactId)) {
                if (library.containsKey("directory")) {
                    dirs.add((String) library["directory"])
                }
                if (library.containsKey("requires")) {
                    for (String dep in library["requires"]) {
                        def (String depGroup, String depArtifact) = splitCoordinates((String) dep)
                        dirs.addAll(getMatchingDirs(depGroup, depArtifact))
                    }
                }
            }
        }

        if (groupId != null && artifactId != null) {
            Path defaultDir = metadataRoot.resolve(groupId.replace(".", "/")).resolve(artifactId)
            if (defaultDir.resolve("index.json").toFile().exists()) {
                dirs.add(defaultDir.toString())
            }
        }
        return dirs
    }

    /**
     * Generates a list of all test invocations that match given group ID, artifact ID and version combination.
     * null values match every possible value (null artifact ID matches all artifacts in given group).
     *
     * @param groupId group ID to match
     * @param artifactId artifact ID to match
     * @param version version to match
     * @return list in which every entry holds complete information required to perform a single test invocation
     */
    static List<Map<String, ?>> generateTestInvocations(String groupId, String artifactId, String version) {
        List<Map<String, ?>> invocations = new ArrayList<>()

        Set<String> matchingDirs = getMatchingDirs(groupId, artifactId)
        for (String directory in matchingDirs) {
            Path fullDir = metadataRoot.resolve(directory)
            Path index = fullDir.resolve("index.json")
            def data = extractJsonFile(index)

            for (def library in data) {
                if (coordinatesMatch((String) library["module"], groupId, artifactId)) {
                    Path metadataDir = fullDir.resolve((String) library["metadata-version"])


                    Path testDir = testRoot.resolve((String) library["test-directory"])
                    Path testIndexPath = testDir.resolve("index.json")
                    List<String> cmd
                    Map<String, String> env = null

                    if (testIndexPath.toFile().exists()) {
                        def tests = extractJsonFile(testIndexPath)
                        if (tests["test-command"] instanceof String) {
                            cmd = tests["test-command"].split()
                        } else {
                            cmd = (List<String>) tests["test-command"]
                        }
                        if (tests.hasProperty("test-environment")) {
                            env = (Map<String, String>) tests["test-environment"]
                        }
                    } else {
                        cmd = ["gradle", "nativeTest"]
                    }

                    for (String tested in library["tested-versions"]) {
                        if (version == null || tested == version) {
                            String coordinates = library["module"] + ":" + tested
                            def (String libraryGroup, String libraryArtifact, String libraryVersion) = splitCoordinates(coordinates)
                            List<String> versionCmd = cmd.stream()
                                    .map((String c) -> processCommand(c, metadataDir,
                                            libraryGroup, libraryArtifact, libraryVersion))
                                    .collect(Collectors.toList())

                            invocations.add(["coordinates"       : coordinates,
                                             "library-version"   : libraryVersion,
                                             "metadata-directory": metadataDir,
                                             "test-directory"    : testDir,
                                             "test-command"      : versionCmd,
                                             "test-environment"  : env])
                        }
                    }
                }
            }
        }
        invocations = invocations.unique()
        return invocations
    }

    /**
     * Returns a list of metadata files in a given directory.
     *
     * @param directory
     * @return list of json files contained in it
     */
    static List<String> getMetadataFileList(Path directory) {
        List<String> foundFiles = new ArrayList<>()
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.filter(Files::isRegularFile)
                    .map(p -> p.fileName.toString())
                    .filter(s -> s.endsWith(".json"))
                    .filter(s -> !s.endsWith("index.json"))
                    .forEach(s -> foundFiles.add(s))
        }
        Path indexFile = directory.resolve("index.json")
        if (indexFile.toFile().exists()) {
            List<String> indexFiles = (List<String>) extractJsonFile(indexFile)
            assert indexFiles.toSet() == foundFiles.toSet(), "Metadata file list in '${indexFile.toAbsolutePath()}' is not up to date!"
            return indexFiles
        } else {
            return foundFiles
        }
    }

    /**
     * Fills in template parameters in the command invocation.
     * Parameters are defined as <param_name> in cmd.
     *
     * @param cmd command line with parameters
     * @param metadataDir metadata directory location
     * @param groupId group ID
     * @param artifactId artifact ID
     * @param version version
     * @return final command
     */
    static String processCommand(String cmd, Path metadataDir, String groupId, String artifactId, String version) {
        return cmd.replace("<metadata_dir>", metadataDir.toAbsolutePath().toString())
                .replace("<group_id>", groupId)
                .replace("<artifact_id>", artifactId)
                .replace("<version>", version)
    }

    /**
     * Returns a list of test invocations that matches changed files between base and new commit.
     * @param baseCommit
     * @param newCommit
     * @param rootDir
     * @return List of test invocations
     */
    @SuppressWarnings("unused")
    static List<Map<String, ?>> diffTestInvocations(String baseCommit, String newCommit) {
        String cmd = "git diff --name-only --diff-filter=ACMRT ${baseCommit} ${newCommit}"

        Process p = cmd.execute()
        String output = p.in.text
        List<String> diffFiles = Arrays.asList(output.split("\\r?\\n"))
        Set<Path> changedTests = new HashSet<Path>()
        Set<Path> changedMetadata = new HashSet<Path>()

        for (String line in diffFiles) {
            Path dirAbspath = repoRoot.resolve(line)
            if (dirAbspath.startsWith(testRoot)) {
                changedTests.add(dirAbspath)
            } else if (dirAbspath.startsWith(metadataRoot)) {
                changedMetadata.add(dirAbspath)
            }
        }

        List<Map<String, ?>> invocations
        invocations = generateTestInvocations(null, null, null)

        Set<Map<String, ?>> matchingInvocations = new HashSet<>()
        for (Map<String, ?> inv in invocations) {
            boolean added = false

            for (Path metadata in changedMetadata) {
                if (metadata.startsWith((Path) inv["metadata-directory"])) {
                    matchingInvocations.add(inv)
                    added = true
                }
            }

            if (added) {
                continue
            }

            for (Path test in changedTests) {
                if (test.startsWith(inv["test-directory"].toString())) {
                    matchingInvocations.add(inv)
                }
            }
        }
        return matchingInvocations.toList()
    }
}
