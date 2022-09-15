package org.graalvm.internal.common

import org.graalvm.internal.tck.Utils
import org.graalvm.internal.tck.harness.MetadataLookupLogic

import java.nio.file.Path

import static org.graalvm.internal.tck.TestUtils.metadataRoot
import static org.graalvm.internal.tck.TestUtils.testRoot

class MetadataTest {

    private static String INDEX_FILE = "index.json"

    private String group
    private String artifact
    private String version
    private Path metadataDir
    private Path testDir
    private boolean override

    MetadataTest(String coordinates) {
        def (String group, String artifact, String version) = Utils.splitCoordinates(coordinates)
        this.group = group
        this.artifact = artifact
        this.version = version
        this.override = false
        this.metadataDir = findMetadataDir()
        this.testDir = findTestDir()
    }

    /**
     *  Generates GAV coordinate
     *
     * @return GAV coordinates
     */
    String getGAVCoordinates() {
        return "${group}:${artifact}:${version}"
    }

    Path getMetadataDir() {
        return metadataDir
    }

    Path getTestDir() {
        return testDir
    }

    String getVersion() {
        return version
    }

    String getGroup() {
        return group
    }

    String getArtifact() {
        return artifact
    }

    boolean getOverride() {
        return override
    }
/**
     * Generates coordinate specific task name.
     *
     * @param prefix
     * @return coordinate specific task name
     */
    String generateTaskName(String taskName) {
        return "${taskName}-${getGAVCoordinates().replace(":", "-")}"
    }

    /**
     * Returns a set of all directories that match given group ID and artifact ID.
     * null values match every possible value (null artifact ID matches all artifacts in given group).
     *
     * @return set of all directories that match given criteria
     */
    private Set<String> getMatchingMetadataDirs() {
        Set<String> dirs = new HashSet<String>()
        for (Map<String, ?> library in (Utils.readIndexFile(metadataRoot) as List<Map<String, ?>>)) {
            if (Utils.coordinatesMatch((String) library["module"], group, artifact)) {
                if (library.containsKey("directory")) {
                    dirs.add((String) library["directory"])
                }
                if (library.containsKey("requires")) {
                    for (String dep in library["requires"]) {
                        def (String depGroup, String depArtifact) = Utils.splitCoordinates((String) dep)
                        dirs.addAll(MetadataLookupLogic.getMatchingMetadataDirs(depGroup, depArtifact))
                    }
                }
            }
        }

        if (group != null && artifact != null) {
            // Let's see if library wasn't added to index but is present on the disk.
            Path defaultDir = metadataRoot.resolve(group).resolve(artifact)
            if (defaultDir.resolve("index.json").toFile().exists()) {
                dirs.add(defaultDir.toString())
            }
        }
        return dirs
    }


    /**
     * Returns metadata directory for given full coordinates
     *
     * @return path to metadata directory
     */
    private Path findMetadataDir() {
        Objects.requireNonNull(group, "Group ID must be specified")
        Objects.requireNonNull(artifact, "Artifact ID must be specified")
        Objects.requireNonNull(version, "Version must be specified")

        Set<String> matchingDirs = getMatchingMetadataDirs()
        for (String directory in matchingDirs) {
            Path fullDir = metadataRoot.resolve(directory)
            Path index = fullDir.resolve(INDEX_FILE)

            def metadataIndex = Utils.extractJsonFile(index)
            for (def entry in metadataIndex) {
                if (Utils.coordinatesMatch((String) entry["module"], group, artifact) && ((List<String>) entry["tested-versions"]).contains(version)) {
                    if (entry.containsKey("override")) {
                        this.override = entry["override"] as boolean
                    }
                    Path metadataDir = fullDir.resolve(version)
                    return metadataDir
                }
            }
        }

        throw new RuntimeException("Missing metadata for ${getGAVCoordinates()}")
    }

    /**
     * Given full coordinates returns matching test directory
     *
     * @return matching test directory
     */
    private Path findTestDir() {
        Objects.requireNonNull(group, "Group ID must be specified")
        Objects.requireNonNull(artifact, "Artifact ID must be specified")
        Objects.requireNonNull(version, "Version must be specified")

        // First, let's try if we can find test directory from the new `tests/src/index.json` file.
        List<Map<String, ?>> index = Utils.readIndexFile(testRoot) as List<Map<String, ?>>
        for (Map<String, ?> entry in index) {
            boolean found = ((List<Map<String, ?>>) entry.get("libraries")).stream().anyMatch(
                    lib -> {
                        return Utils.coordinatesMatch((String) lib.get("name"), group, artifact) &&
                                ((List<String>) lib.get("versions")).contains(version)
                    }
            )
            if (found) {
                return testRoot.resolve((String) entry.get("test-project-path"))
            }
        }
        throw new RuntimeException("Missing test-directory for coordinates `${getGAVCoordinates()}`")
    }
}
