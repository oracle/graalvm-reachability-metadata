/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package org.graalvm.internal.tck.harness

import org.graalvm.internal.common.MetadataDescriptor

import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import java.util.stream.Stream

import static org.graalvm.internal.tck.RepoScanner.metadataRoot
import static org.graalvm.internal.tck.Utils.coordinatesMatch
import static org.graalvm.internal.tck.Utils.extractJsonFile
import static org.graalvm.internal.tck.Utils.readIndexFile
import static org.graalvm.internal.tck.Utils.splitCoordinates

/**
 * Class that provides static methods that are used to fetch correct metadata.
 */
class MetadataLookupLogic {

    static String INDEX_FILE = "index.json"

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
                    .filter(s -> s.endsWith(".json") && !s.endsWith(INDEX_FILE))
                    .forEach(s -> foundFiles.add(s))
        }
        Path indexFile = directory.resolve(INDEX_FILE)
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
    static Set<String> getMatchingMetadataDirs(String groupId = null, String artifactId = null) {
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
            if (defaultDir.resolve(INDEX_FILE).toFile().exists()) {
                dirs.add(defaultDir.toString())
            }
        }

        return dirs
    }

    /**
     * Returns all coordinates that match given coordinate filter.
     * @param coordinateFilter
     * @return list of all coordinates that
     */
    static List<MetadataDescriptor> getDescriptorsForCoordinates(String coordinateFilter = "") {
        def (String groupId, String artifactId, String version) = splitCoordinates(coordinateFilter)

        Set<String> matchingCoordinates = new HashSet<>()

        for (String directory in getMatchingMetadataDirs(groupId, artifactId)) {
            Path index = metadataRoot.resolve(directory).resolve(INDEX_FILE)
            def metadataIndex = extractJsonFile(index)

            for (def entry in metadataIndex) {
                List<String> coordinates = splitCoordinates((String) entry["module"])
                List<String> testedVersions = entry["tested-versions"] as List<String>
                if (coordinatesMatch((String) entry["module"], groupId, artifactId) && (version == null || testedVersions.contains(version))) {
                    if (version == null) { // We want all library versions, so let's add them.
                        testedVersions.stream()
                                .filter(t -> metadataRoot.resolve(coordinates.get(0)).resolve(coordinates.get(1)).resolve(t).toFile().exists())
                                .forEach(t -> matchingCoordinates.add("${entry["module"]}:${t}"))
                    } else { // We have a specific version pinned.
                        if (metadataRoot.resolve(coordinates.get(0)).resolve(coordinates.get(1)).resolve(version).toFile().exists()) {
                            matchingCoordinates.add("${entry["module"]}:${version}")
                        }
                    }
                }
            }
        }

        return matchingCoordinates.stream().map(c -> new MetadataDescriptor(c)).collect(Collectors.toList()).toList()
    }

    static List<MetadataDescriptor> getAllDescriptors() {
        return getDescriptorsForCoordinates()
    }

}
