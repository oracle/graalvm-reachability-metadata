package org.graalvm.internal.tck;

import groovy.json.JsonSlurper;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Checks content of config files for a new library.
 * <p>
 * Run with {@code gradle checkConfigFiles --coordinates com.example:library:1.0.0}.
 *
 * @author David Nestorovic
 */
public class ConfigFilesChecker extends DefaultTask {

    private final List<String> EXPECTED_FILES = Arrays.asList("index.json", "reflect-config.json", "resource-config.json", "serialization-config.json",
            "jni-config.json", "proxy-config.json", "predefined-classes-config.json");

    private final List<String> ILLEGAL_TYPE_VALUES = List.of("java.lang");

    private String coordinates;

    @Option(option = "coordinates", description = "Coordinates in the form of group:artifact:version")
    void setCoordinates(String coordinates) {
        this.coordinates = coordinates;
    }


    @TaskAction
    void run() throws IllegalArgumentException {
        Coordinates coordinates = Coordinates.parse(this.coordinates);
        if (coordinates.group().equalsIgnoreCase("org.example") || coordinates.group().equalsIgnoreCase("samples")) {
            return;
        }

        File coordinatesMetadataRoot = getProject().file(CoordinateUtils.replace("metadata/$group$/$artifact$/$version$", coordinates));
        if (!coordinatesMetadataRoot.exists()) {
            throw new IllegalArgumentException("ERROR: Cannot find metadata directory for given coordinates: " + this.coordinates);
        }

        boolean containsErrors = false;
        List<File> filesInMetadata = getConfigFilesForMetadataDir(coordinatesMetadataRoot);
        for (File file : filesInMetadata) {
            if (file.getName().equalsIgnoreCase("reflect-config.json")) {
                containsErrors |= reflectConfigFilesContainsErrors(file);
            }

            if (file.getName().equalsIgnoreCase("resource-config.json")) {
                containsErrors |= resourceConfigFilesContainsErrors(file);
            }

            if (file.getName().equalsIgnoreCase("serialization-config.json")) {
                containsErrors |= serializationConfigFilesContainsErrors(file);
            }

            if (file.getName().equalsIgnoreCase("proxy-config.json")) {
                containsErrors |= proxyConfigFilesContainsErrors(file);
            }

            if (file.getName().equalsIgnoreCase("jni-config.json")) {
                containsErrors |= jniConfigFilesContainsErrors(file);
            }
        }

        if (containsErrors) {
            throw new IllegalStateException("Errors above found for: " + this.coordinates);
        }
    }

    private List<File> getConfigFilesForMetadataDir(File root) throws RuntimeException {
        List<File> files = new ArrayList<>();
        File [] content = root.listFiles();

        if (content == null) {
            throw new RuntimeException("ERROR: Failed to load content of " + root.toURI());
        }

        Arrays.stream(content).forEach(file -> {
            String fileName = file.getName();

            if (EXPECTED_FILES.stream().noneMatch(f -> f.equalsIgnoreCase(fileName))) {
                throw new IllegalStateException("ERROR: Unexpected file " + file.toURI() + " found in " + root.toURI());
            }

            files.add(file);
        });

        return files;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getConfigEntries(File file) {
        JsonSlurper js = new JsonSlurper();
        return ((List<Object>) js.parse(file)).stream().map(e -> (Map<String, Object>)e).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getConfigEntry(File file) {
        JsonSlurper js = new JsonSlurper();
        return (Map<String, Object>) js.parse(file);
    }

    private boolean reflectConfigFilesContainsErrors(File file) {
        List<Map<String, Object>> entries = getConfigEntries(file);

        if (entries.size() == 0) {
            System.out.println("ERROR: empty reflect-config detected: " + file.toURI());
            return true;
        }

        boolean containsErrors = containsDuplicatedEntries(entries, file);
        for (var entry : entries) {
            containsErrors |= checkTypeReachable(entry, file);
        }

        return containsErrors;
    }

    @SuppressWarnings("unchecked")
    private boolean resourceConfigFilesContainsErrors(File file) {
        Map<String, Object> entries = getConfigEntry(file);
        List<Map<String, Object>> bundles = (List<Map<String, Object>>) entries.get("bundles");
        Map<String, Object> resources = (Map<String, Object>) entries.get("resources");

        boolean containsErrors = false;
        if (resources != null) {
            List<Map<String, Object>> includes = (List<Map<String, Object>>) resources.get("includes");
            List<Map<String, Object>> excludes = (List<Map<String, Object>>) resources.get("excludes");

            if (listNullOrEmpty(includes) && listNullOrEmpty(excludes) && listNullOrEmpty(bundles)){
                System.out.println("ERROR: empty resource-config detected: " + file.toURI());
                return true;
            }

            // check include entries
            if (includes != null) {
                containsErrors |= containsDuplicatedEntries(includes, file);

                for (var entry : includes) {
                    containsErrors |= checkTypeReachable(entry, file);
                }
            }

            // check exclude entries
            if (excludes != null) {
                containsErrors |= containsDuplicatedEntries(excludes, file);

                for (var entry : excludes) {
                    containsErrors |= checkTypeReachable(entry, file);
                }
            }
        }

        return containsErrors;
    }

    @SuppressWarnings("unchecked")
    private boolean serializationConfigFilesContainsErrors(File file) {
        Map<String, Object> entries = getConfigEntry(file);

        List<Map<String, Object>> types = (List<Map<String, Object>>) entries.get("types");
        List<Map<String, Object>> lambdaCapturingTypes = (List<Map<String, Object>>) entries.get("lambdaCapturingTypes");

        if (listNullOrEmpty(types) && listNullOrEmpty(lambdaCapturingTypes)) {
            System.out.println("ERROR: empty serialization-config detected: " + file.toURI());
            return true;
        }

        boolean containsErrors = false;
        if (types != null) {
            // check include entries
            containsErrors |= containsDuplicatedEntries(types, file);

            for (var entry : types) {
                containsErrors |= checkTypeReachable(entry, file);
            }
        }

        if (lambdaCapturingTypes != null) {
            // check include entries
            containsErrors |= containsDuplicatedEntries(lambdaCapturingTypes, file);

            for (var entry : lambdaCapturingTypes) {
                containsErrors |= checkTypeReachable(entry, file);
            }
        }

        return containsErrors;
    }

    private boolean proxyConfigFilesContainsErrors(File file) {
        List<Map<String, Object>> entries = getConfigEntries(file);

        if (entries.size() == 0) {
            System.out.println("ERROR: empty proxy-config detected: " + file.toURI());
            return true;
        }

        boolean containsErrors = containsDuplicatedEntries(entries, file);
        for (var entry : entries) {
            containsErrors |= checkTypeReachable(entry, file);
        }

        return containsErrors;
    }

    private boolean jniConfigFilesContainsErrors(File file) {
        List<Map<String, Object>> entries = getConfigEntries(file);

        if (entries.size() == 0) {
            System.out.println("ERROR: empty jni-config detected: " + file.toURI());
            return true;
        }

        return containsDuplicatedEntries(entries, file);
    }

    private boolean containsDuplicatedEntries(List<Map<String, Object>> entries, File file) {
        Map<Map<String, Object>, Integer> duplicates = new HashMap<>();
        entries.forEach(entry -> duplicates.merge(entry, 1, Integer::sum));

        // print all entries that appears more than once
        boolean containsDuplicates = false;
        for (var entry : duplicates.entrySet()) {
            if (entry.getValue() > 1) {
                String entryName = (String) entry.getKey().get("name");
                if (entryName == null) {
                    entryName = entry.getKey().toString();
                }
                containsDuplicates = true;
                System.out.println("ERROR: In file " + file.toURI() + " there is a duplicated entry " + entryName);
            }
        }

        return containsDuplicates;
    }

    @SuppressWarnings("unchecked")
    private boolean checkTypeReachable(Map<String, Object> entry, File file) {
        // check if condition entry exists
        Map<String, Object> condition = (Map<String, Object>) entry.get("condition");
        if (condition == null) {
            System.out.println("ERROR: In file " + file.toURI() + " there is an entry " + entry + " with missing condition field.");
            return true;
        }

        // check if typeReachable exists inside condition entry
        String typeReachable = (String) condition.get("typeReachable");
        if (ILLEGAL_TYPE_VALUES.stream().anyMatch(typeReachable::startsWith)) {
            // get name of entry that misses typeReachable. If name cannot be determinate, write whole entry
            String entryName = (String) entry.get("name");
            if (entryName == null) {
                entryName = entry.toString();
            }

            System.out.println("ERROR: In file " + file.toURI() + " entry: " + entryName + " contains illegal typeReachable value. Field" +
                    " typeReachable cannot be any of the following values: " + ILLEGAL_TYPE_VALUES);
            return true;
        }

        return false;
    }

    private boolean listNullOrEmpty(List list) {
        return list == null || list.size() == 0;
    }

}
