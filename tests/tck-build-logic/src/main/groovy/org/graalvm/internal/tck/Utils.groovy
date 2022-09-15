/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package org.graalvm.internal.tck

import groovy.json.JsonSlurper

import java.nio.file.Path

class Utils {
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
     * Returns contents of an index.json file from given directory.
     *
     * @return parsed contents of index file
     */
    static Object readIndexFile(Path dir) {
        return extractJsonFile(dir.resolve('index.json'))
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
}
