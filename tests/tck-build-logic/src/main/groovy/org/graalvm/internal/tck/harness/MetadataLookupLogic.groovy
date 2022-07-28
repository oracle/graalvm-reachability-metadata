/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package org.graalvm.internal.tck.harness

import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream

import static org.graalvm.internal.tck.TestUtils.metadataRoot
import static org.graalvm.internal.tck.Utils.coordinatesMatch
import static org.graalvm.internal.tck.Utils.extractJsonFile
import static org.graalvm.internal.tck.Utils.readIndexFile
import static org.graalvm.internal.tck.Utils.splitCoordinates

/**
 * Class that provides static methods that are used to fetch correct metadata.
 */
class MetadataLookupLogic {
    static Map<String, List<Path>> universe
    private static List<Path> processedIndexes = []

    private static Map<String, List<Path>> initMetadataUniverse() {
        universe = new HashMap<>()

        // We are looking for metadata/<group_id>/<artifact_id>/index.json
        try (Stream<Path> paths = Files.walk(metadataRoot, 3)) {
            paths.filter(Files::isRegularFile)
                    .filter(e -> e.getNameCount() - metadataRoot.getNameCount() == 3)
                    .filter(s -> s.endsWith("index.json"))
                    .forEach(this::explodeLibraryIndex)
        }
    }

    /**
     * Populates universe with data contained in the given index file
     * @param index
     */
    private static void explodeLibraryIndex(Path index) {
        println("Explodes for ${index}")
        if (processedIndexes.contains(index)) {
            return
        }
        processedIndexes.add(index)

        List<Map<String, Object>> metadataIndex = extractJsonFile(index)
        for (entry in metadataIndex) {
            String module = (String) entry["module"]
            List<String> tested_versions = (List<String>) entry["tested-versions"]

            List<Path> metadataPaths = []
            if (entry.containsKey("metadata-version")) {
                metadataPaths.add(index.toFile().parentFile.toPath().resolve((String) entry["metadata-version"]))
            }
            if (entry.containsKey("requires-metadata")) {
                for (String gav : ((List<String>) entry["requires-metadata"])) {
                    if (!universe.containsKey(gav)) {
                        def (String groupId, String artifactId, _) = splitCoordinates(gav)
                        println("Trying ${gav}")
                        explodeLibraryIndex(metadataRoot.resolve(groupId).resolve(artifactId).resolve(index))
                    }
                    if (!universe.containsKey(gav)) {
                        println(universe)
                        throw new RuntimeException("Missing metadata for ${gav}")
                    }
                    metadataPaths.addAll((List<Path>) universe.get(gav))
                }
            }
            for (String tested_version : tested_versions) {
                String resultingGav = "${module}:${tested_version}"
                universe.put(resultingGav, metadataPaths)
            }
        }
    }

    static {
        initMetadataUniverse() // Make sure we can use this ASAP
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
                    .filter(s -> s.endsWith(".json") && !s.endsWith("index.json"))
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
     * Returns a set of all directories that match given group ID and artifact ID.
     * null values match every possible value (null artifact ID matches all artifacts in given group).
     *
     * @return set of all directories that match given criteria
     */
    static Set<String> getMatchingMetadataDirs(String groupId, String artifactId) {
        Set<String> dirs = new HashSet<String>()
        for (Map<String, ?> library in (readIndexFile(metadataRoot) as List<Map<String, ?>>)) {
            if (coordinatesMatch((String) library["module"], groupId, artifactId)) {
                if (library.containsKey("directory")) {
                    dirs.add((String) library["directory"])
                }
                if (library.containsKey("requires")) {
                    for (String dep in library["requires"]) {
                        def (String depGroup, String depArtifact) = splitCoordinates((String) dep)
                        dirs.addAll(getMatchingMetadataDirs(depGroup, depArtifact))
                    }
                }
            }
        }

        if (groupId != null && artifactId != null) {
            // Let's see if library wasn't added to index but is present on the disk.
            Path defaultDir = metadataRoot.resolve(groupId).resolve(artifactId)
            if (defaultDir.resolve("index.json").toFile().exists()) {
                dirs.add(defaultDir.toString())
            }
        }
        return dirs
    }

    /**
     * Returns metadata directories for given full coordinates
     * @param coordinates
     * @return list of paths to metadata directories
     */
    static List<Path> getMetadataDirs(String coordinates) {
        def (String groupId, String artifactId, String version) = splitCoordinates((String) coordinates)
        Objects.requireNonNull(groupId, "Group ID must be specified")
        Objects.requireNonNull(artifactId, "Artifact ID must be specified")
        Objects.requireNonNull(version, "Version must be specified")

        if (!universe.containsKey(coordinates)) {
            throw new RuntimeException("Missing metadata for ${coordinates}")
        }
        return universe.get(coordinates)
    }

    /**
     * Returns all coordinates that match given coordinate filter.
     * @param coordinateFilter
     * @return list of all coordinates that match given filter
     */
    static List<String> getMatchingCoordinates(String coordinateFilter) {
        def (String groupId, String artifactId, String version) = splitCoordinates(coordinateFilter)
        return universe.keySet().stream()
                .filter(gav -> groupId == null || gav.startsWith("${groupId}"))
                .filter(gav -> artifactId == null || gav.startsWith("${groupId}:${artifactId}"))
                .filter(gav -> version == null || gav.startsWith("${groupId}:${artifactId}:${version}"))
                .toList()
    }
}
