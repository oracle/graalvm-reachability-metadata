package org.graalvm.internal.tck.harness.tasks


import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import groovy.json.JsonOutput
import org.graalvm.internal.tck.model.MetadataVersionsIndexEntry
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.util.internal.VersionNumber

import java.util.regex.Matcher
import java.util.regex.Pattern

@SuppressWarnings("unused")
abstract class FetchExistingLibrariesWithNewerVersionsTask extends DefaultTask {

    @Input
    abstract ListProperty<String> getAllLibraryCoordinates()

    private static final List<String> INFRASTRUCTURE_TESTS = List.of("samples", "org.example")

    /**
     * Identifies library versions, including optional pre-release and ".Final" suffixes.
     * <p>
     * A version is considered a pre-release if it has a suffix (following the last '.' or '-') matching
     * one of these case-insensitive patterns:
     * <ul>
     *   <li>{@code alpha} followed by optional numbers (e.g., "alpha", "Alpha1", "alpha123")</li>
     *   <li>{@code beta} followed by optional numbers (e.g., "beta", "Beta2", "BETA45")</li>
     *   <li>{@code rc} followed by optional numbers (e.g., "rc", "RC1", "rc99")</li>
     *   <li>{@code cr} followed by optional numbers (e.g., "cr", "CR3", "cr10")</li>
     *   <li>{@code m} followed by REQUIRED numbers (e.g., "M1", "m23")</li>
     *   <li>{@code ea} followed by optional numbers (e.g., "ea", "ea2", "ea15")</li>
     *   <li>{@code b} followed by REQUIRED numbers (e.g., "b0244", "b5")</li>
     *   <li>{@code preview} followed by optional numbers (e.g., "preview", "preview1", "preview42")</li>
     *   <li>Numeric suffixes separated by '-' (e.g., "-1", "-123")</li>
     * </ul>
     * <p>
     * Versions ending with ".Final" are treated as full releases of the base version.
     */
    private static final Pattern VERSION_PATTERN = ~/(?i)^(\d+(?:\.\d+)*)(?:\.Final)?(?:[-.](alpha\d*|beta\d*|rc\d*|cr\d*|m\d+|ea\d*|b\d+|\d+|preview)(?:[-.].*)?)?$/

    @TaskAction
    void action() {
        // get all existing libraries
        Set<String> libraries = []
        getAllLibraryCoordinates().get().forEach {
            libraries.add(it.substring(0, it.lastIndexOf(":")))
        }

        // foreach existing library find newer versions than the latest one tested except for infrastructure tests
        List<String> newerVersions = new ArrayList<>()
        libraries.forEach {
            String libraryName = it
            if (INFRASTRUCTURE_TESTS.stream().noneMatch(testName -> libraryName.startsWith(testName))) {
                List<String> versions = getNewerVersionsFor(libraryName, getLatestLibraryVersion(libraryName))
                versions.forEach {
                    newerVersions.add(libraryName.concat(":").concat(it))
                }
            }
        }

        def map = [:]
        newerVersions.each { coord ->
            def (group, artifact, version) = coord.tokenize(':')
            def key = "${group}:${artifact}"
            map[key] = (map[key] ?: []) + version
        }
        def pairs = map.collect { k, v -> [name: k, versions: v] }

        println JsonOutput.toJson(pairs)
    }

    static List<String> getNewerVersionsFor(String library, String startingVersion) {
        def baseUrl = "https://repo1.maven.org/maven2"
        String[] libraryParts = library.split(":")
        String group = libraryParts[0].replace(".", "/")
        String artifact = libraryParts[1]
        def data = new URL(baseUrl + "/" + group + "/" + artifact + "/" + "maven-metadata.xml").getText()

        List<String> newerVersions = getNewerVersionsFromLibraryIndex(data, startingVersion, library)

        // filter pre-release versions if full release exists
        return filterPreReleases(newerVersions)
    }

    static List<String> getNewerVersionsFromLibraryIndex(String index, String startingVersion, String libraryName) {
        Pattern pattern = Pattern.compile("<version>(.*)</version>");
        Matcher matcher = pattern.matcher(index);
        List<String> allVersions = new ArrayList<>();

        if (matcher.groupCount() < 1) {
            throw new RuntimeException("Cannot find versions in the given index file: " + libraryName);
        }

        while (matcher.find()) {
            allVersions.add(matcher.group(1));
        }

        int indexOfStartingVersion = allVersions.indexOf(startingVersion);
        if (indexOfStartingVersion < 0) {
            return new ArrayList<>();
        }

        allVersions = allVersions.subList(indexOfStartingVersion, allVersions.size());

        return allVersions.subList(1, allVersions.size());
    }

    static List<String> filterPreReleases(List<String> versions) {
        // identify full releases
        Set<String> releases = versions.collect { v ->
            def matcher = VERSION_PATTERN.matcher(v)
            if (matcher.matches() && matcher.group(2) == null) {
                return matcher.group(1)
            }
            return null
        }.findAll { it != null } as Set

        // filter pre-releases if full release exists
        return versions.findAll { v ->
            def matcher = VERSION_PATTERN.matcher(v)
            if (matcher.matches()) {
                String base = matcher.group(1)
                String preSuffix = matcher.groupCount() > 1 ? matcher.group(2) : null
                return preSuffix == null || !releases.contains(base)
            }
            true
        }
    }

    static String getLatestLibraryVersion(String libraryModule) {
        try {
            String[] coordinates = libraryModule.split(":");
            String group = coordinates[0];
            String artifact = coordinates[1];

            File coordinatesMetadataIndex = new File("metadata/" + group + "/" + artifact +"/index.json");
            ObjectMapper objectMapper = new ObjectMapper()
                    .enable(SerializationFeature.INDENT_OUTPUT)
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL);

            List<MetadataVersionsIndexEntry> entries = objectMapper.readValue(coordinatesMetadataIndex, new TypeReference<List<MetadataVersionsIndexEntry>>() {
            });

            List<String> allTested = new ArrayList<>();
            for (MetadataVersionsIndexEntry entry : entries) {
                allTested.addAll(entry.testedVersions());
            }

            if (allTested.isEmpty()) {
                throw new IllegalStateException("Cannot find any tested version for: " + libraryModule);
            }

            allTested.sort(Comparator.comparing(VersionNumber::parse));
            return allTested.get(allTested.size() - 1);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
