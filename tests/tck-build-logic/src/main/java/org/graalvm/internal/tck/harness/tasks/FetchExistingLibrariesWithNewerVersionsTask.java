/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.graalvm.internal.tck.TestedVersionUpdaterTask;
import org.graalvm.internal.tck.model.MetadataVersionsIndexEntry;
import org.graalvm.internal.tck.model.SkippedVersionEntry;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.util.internal.VersionNumber;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public abstract class FetchExistingLibrariesWithNewerVersionsTask extends DefaultTask {

    @Input
    public abstract ListProperty<String> getAllLibraryCoordinates();

    private static final List<String> INFRASTRUCTURE_TESTS = List.of("samples", "org.example");

    @TaskAction
    public void action() {
        Set<String> libraries = new LinkedHashSet<>();
        for (String coord : getAllLibraryCoordinates().get()) {
            int last = coord.lastIndexOf(':');
            if (last > 0) {
                libraries.add(coord.substring(0, last));
            }
        }

        List<String> newerVersions = new ArrayList<>();
        for (String libraryName : libraries) {
            if (INFRASTRUCTURE_TESTS.stream().noneMatch(libraryName::startsWith)) {
                List<String> versions = getNewerVersionsFor(libraryName, getLatestLibraryVersion(libraryName));
                List<String> skipped = getSkippedVersions(libraryName);
                versions.removeAll(skipped);
                for (String v : versions) {
                    newerVersions.add(libraryName + ":" + v);
                }
            }
        }

        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (String coord : newerVersions) {
            String[] parts = coord.split(":", -1);
            String key = parts[0] + ":" + parts[1];
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(parts[2]);
        }

        List<Map<String, Object>> pairs = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : grouped.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", e.getKey());
            m.put("versions", e.getValue());
            pairs.add(m);
        }

        try {
            ObjectMapper om = new ObjectMapper()
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL);
            System.out.println(om.writeValueAsString(pairs));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static List<String> getNewerVersionsFor(String library, String startingVersion) {
        try {
            String baseUrl = "https://repo1.maven.org/maven2";
            String[] libraryParts = library.split(":");
            String group = libraryParts[0].replace(".", "/");
            String artifact = libraryParts[1];
            String data = new String(new URL(baseUrl + "/" + group + "/" + artifact + "/maven-metadata.xml").openStream().readAllBytes());

            List<String> newerVersions = getNewerVersionsFromLibraryIndex(data, startingVersion, library);

            List<String> testedVersions = getTestedVersions(library);
            newerVersions.removeAll(testedVersions);

            return filterPreReleases(newerVersions);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static List<String> getNewerVersionsFromLibraryIndex(String index, String startingVersion, String libraryName) {
        Pattern pattern = Pattern.compile("<version>(.*)</version>");
        Matcher matcher = pattern.matcher(index);
        List<String> allVersions = new ArrayList<>();

        while (matcher.find()) {
            allVersions.add(matcher.group(1));
        }

        int indexOfStartingVersion = allVersions.indexOf(startingVersion);
        if (indexOfStartingVersion < 0) {
            return new ArrayList<>();
        }

        allVersions = allVersions.subList(indexOfStartingVersion, allVersions.size());
        return new ArrayList<>(allVersions.subList(1, allVersions.size()));
    }

    static List<String> filterPreReleases(List<String> versions) {
        Set<String> releases = new HashSet<>();
        for (String v : versions) {
            Matcher m = TestedVersionUpdaterTask.VERSION_PATTERN.matcher(v);
            if (m.matches() && m.group(2) == null) {
                releases.add(m.group(1));
            }
        }

        List<String> result = new ArrayList<>();
        for (String v : versions) {
            Matcher m = TestedVersionUpdaterTask.VERSION_PATTERN.matcher(v);
            if (m.matches()) {
                String base = m.group(1);
                String preSuffix = m.groupCount() > 1 ? m.group(2) : null;
                if (preSuffix == null || !releases.contains(base)) {
                    result.add(v);
                }
            } else {
                result.add(v);
            }
        }
        return result;
    }

    static String getLatestLibraryVersion(String libraryModule) {
        try {
            List<String> testedVersions = getTestedVersions(libraryModule);
            if (testedVersions.isEmpty()) {
                throw new IllegalStateException("Cannot find any tested version for: " + libraryModule);
            }
            testedVersions.sort(Comparator.comparing(VersionNumber::parse));
            return testedVersions.get(testedVersions.size() - 1);
        } catch (RuntimeException e) {
            throw e;
        }
    }

    static List<String> getTestedVersions(String libraryModule) {
        try {
            String[] coordinates = libraryModule.split(":");
            String group = coordinates[0];
            String artifact = coordinates[1];

            File indexFile = new File("metadata/" + group + "/" + artifact + "/index.json");
            if (!indexFile.exists()) {
                return Collections.emptyList();
            }

            ObjectMapper objectMapper = new ObjectMapper()
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL);

            List<MetadataVersionsIndexEntry> entries = objectMapper.readValue(
                    indexFile, new TypeReference<List<MetadataVersionsIndexEntry>>() {});
            List<String> testedVersions = new ArrayList<>();
            for (MetadataVersionsIndexEntry entry : entries) {
                testedVersions.addAll(entry.testedVersions());
            }
            return testedVersions;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static List<String> getSkippedVersions(String libraryModule) {
        try {
            String[] coordinates = libraryModule.split(":");
            String group = coordinates[0];
            String artifact = coordinates[1];

            File coordinatesMetadataIndex = new File("metadata/" + group + "/" + artifact + "/index.json");
            if (!coordinatesMetadataIndex.exists()) {
                throw new RuntimeException("Missing index.json for " + libraryModule);
            }

            ObjectMapper objectMapper = new ObjectMapper()
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL);

            List<MetadataVersionsIndexEntry> entries = objectMapper.readValue(
                    coordinatesMetadataIndex, new TypeReference<List<MetadataVersionsIndexEntry>>() {});

            List<String> skipped = new ArrayList<>();
            for (MetadataVersionsIndexEntry entry : entries) {
                if (entry.skippedVersions() != null) {
                    for (SkippedVersionEntry sve : entry.skippedVersions()) {
                        skipped.add(sve.version());
                    }
                }
            }
            return skipped;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
