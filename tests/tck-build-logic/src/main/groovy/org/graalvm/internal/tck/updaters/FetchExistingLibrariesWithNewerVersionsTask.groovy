package org.graalvm.internal.tck.updaters


import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import groovy.json.JsonOutput
import org.graalvm.internal.tck.model.MetadataVersionsIndexEntry
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.util.internal.VersionNumber

import java.util.regex.Matcher
import java.util.regex.Pattern


abstract class FetchExistingLibrariesWithNewerVersionsTask extends DefaultTask {

    @Input
    abstract ListProperty<String> getAllLibraryCoordinates()

    @Input
    @Option(option = "matrixLimit", description = "Sets the maximum number of coordinates in the final matrix")
    abstract Property<Integer> getMatrixLimit()

    private static final List<String> INFRASTRUCTURE_TESTS = List.of("samples", "org.example")

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

        if (newerVersions.size() > getMatrixLimit().get()) {
            newerVersions = newerVersions.subList(0, getMatrixLimit().get())
        }

        def map = [:]
        newerVersions.each { coord ->
            def (group, artifact, version) = coord.tokenize(':')
            def key = "${group}:${artifact}"
            map[key] = (map[key] ?: []) + version
        }
        def pairs = map.collect { k, v -> [name: k, versions: v] }

        new File(System.getenv("GITHUB_OUTPUT")).append(JsonOutput.toJson(pairs))
    }

    static List<String> getNewerVersionsFor(String library, String startingVersion) {
        def baseUrl = "https://repo1.maven.org/maven2"
        String[] libraryParts = library.split(":")
        String group = libraryParts[0].replace(".", "/")
        String artifact = libraryParts[1]
        def data = new URL(baseUrl + "/" + group + "/" + artifact + "/" + "maven-metadata.xml").getText()

        return getNewerVersionsFromLibraryIndex(data, startingVersion, library)
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
