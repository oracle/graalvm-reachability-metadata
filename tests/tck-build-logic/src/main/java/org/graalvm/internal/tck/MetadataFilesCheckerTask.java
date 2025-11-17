package org.graalvm.internal.tck;

import groovy.json.JsonSlurper;
import org.graalvm.internal.tck.utils.CoordinateUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Checks content of config files for a new library.
 * <p>
 * Run with {@code gradle checkMetadataFiles -Pcoordinates com.example:library:1.0.0}.
 */
@SuppressWarnings("unused")
public abstract class MetadataFilesCheckerTask extends DefaultTask {

    @InputFiles
    protected abstract RegularFileProperty getMetadataRoot();

    @InputFiles
    protected abstract RegularFileProperty getIndexFile();

    private final Set<String> EXPECTED_FILES = new HashSet<>(List.of(
            "index.json",
            "reflect-config.json",
            "resource-config.json",
            "serialization-config.json",
            "jni-config.json",
            "proxy-config.json"));

    private final Set<String> ILLEGAL_TYPE_VALUES = new HashSet<>(List.of("java.lang"));

    private final Set<String> PREDEFINED_ALLOWED_PACKAGES = new HashSet<>(List.of("java.lang", "java.util"));

    Coordinates coordinates;
    List<String> allowedPackages;

    @Option(option = "coordinates", description = "Coordinates in the form of group:artifact:version")
    void setCoordinates(String coords) {
        this.coordinates = Coordinates.parse(coords);

        File coordinatesMetadataRoot = getProject().file(CoordinateUtils.replace("metadata/$group$/$artifact$/$version$", coordinates));
        getMetadataRoot().set(coordinatesMetadataRoot);

        File index = getProject().file("metadata/index.json");
        getIndexFile().set(index);

        this.allowedPackages = getAllowedPackages();
    }


    @TaskAction
    void run() throws IllegalArgumentException, FileNotFoundException {
        if (coordinates.group().equalsIgnoreCase("org.example") || coordinates.group().equalsIgnoreCase("samples")) {
            return;
        }

        File coordinatesMetadataRoot = getMetadataRoot().get().getAsFile();
        if (!coordinatesMetadataRoot.exists()) {
            throw new IllegalArgumentException("ERROR: Cannot find metadata directory for given coordinates: " + this.coordinates);
        }

        boolean containsErrors = false;
        List<File> filesInMetadata = getConfigFilesForMetadataDir(coordinatesMetadataRoot);
        for (File file : filesInMetadata) {
            if (file.getName().equalsIgnoreCase("reflect-config.json")) {
                containsErrors |= reflectConfigFileContainsErrors(file);
            }

            if (file.getName().equalsIgnoreCase("resource-config.json")) {
                containsErrors |= resourceConfigFileContainsErrors(file);
            }

            if (file.getName().equalsIgnoreCase("serialization-config.json")) {
                containsErrors |= serializationConfigFileContainsErrors(file);
            }

            if (file.getName().equalsIgnoreCase("proxy-config.json")) {
                containsErrors |= proxyConfigFileContainsErrors(file);
            }

            if (file.getName().equalsIgnoreCase("jni-config.json")) {
                containsErrors |= jniConfigFileContainsErrors(file);
            }
        }

        if (containsErrors) {
            throw new IllegalStateException("Errors above found for: " + this.coordinates);
        }
    }

    private List<File> getConfigFilesForMetadataDir(File root) throws RuntimeException {
        List<File> files = new ArrayList<>();
        File[] content = root.listFiles();

        if (content == null) {
            throw new RuntimeException("ERROR: Failed to load content of " + root.toURI());
        }

        Arrays.stream(content).forEach(file -> {
            if (EXPECTED_FILES.stream().noneMatch(file.getName()::equalsIgnoreCase)) {
                throw new IllegalStateException("ERROR: Unexpected file " + file.toURI() + " found in " + root.toURI());
            }

            files.add(file);
        });

        return files;
    }

    @SuppressWarnings("unchecked")
    // in case when config file is an array of entries
    private List<Map<String, Object>> getConfigEntries(File file) {
        return ((List<Object>) new JsonSlurper()
                .parse(file))
                .stream()
                .map(e -> (Map<String, Object>) e)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    // in case when config file is an object
    private Map<String, Object> getConfigEntry(File file) {
        return (Map<String, Object>) new JsonSlurper().parse(file);
    }

    private boolean reflectConfigFileContainsErrors(File file) {
        List<Map<String, Object>> entries = getConfigEntries(file);

        if (entries.isEmpty()) {
            System.out.println("ERROR: empty reflect-config detected: " + file.toURI());
            return true;
        }

        boolean containsErrors = containsDuplicatedEntries(entries, file);
        for (var entry : entries) {
            containsErrors |= checkTypeReachable(entry, file);
            containsErrors |= containsEntriesNotFromLibrary(entry, file);
        }

        return containsErrors;
    }

    @SuppressWarnings("unchecked")
    private boolean resourceConfigFileContainsErrors(File file) {
        Map<String, Object> entries = getConfigEntry(file);
        List<Map<String, Object>> bundles = (List<Map<String, Object>>) entries.get("bundles");
        Map<String, Object> resources = (Map<String, Object>) entries.get("resources");

        boolean containsErrors = false;
        if (resources != null) {
            List<Map<String, Object>> includes = (List<Map<String, Object>>) resources.get("includes");
            List<Map<String, Object>> excludes = (List<Map<String, Object>>) resources.get("excludes");

            if (listNullOrEmpty(includes) && listNullOrEmpty(excludes) && listNullOrEmpty(bundles)) {
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
                    containsErrors |= containsEntriesNotFromLibrary(entry, file);
                }
            }
        }

        return containsErrors;
    }

    private boolean checkOldSerializationConfig(File file) {
        List<Map<String, Object>> entries = getConfigEntries(file);

        if (entries.isEmpty()) {
            System.out.println("ERROR: empty serialization-config detected: " + file.toURI());
            return true;
        }

        boolean containsErrors = containsDuplicatedEntries(entries, file);
        for (var entry : entries) {
            containsErrors |= checkTypeReachable(entry, file);
            containsErrors |= containsEntriesNotFromLibrary(entry, file);
        }

        return containsErrors;
    }

    @SuppressWarnings("unchecked")
    private boolean checkNewSerializationConfig(File file) {
        boolean containsErrors = false;
        Map<String, Object> entries = getConfigEntry(file);

        List<Map<String, Object>> types = (List<Map<String, Object>>) entries.get("types");
        List<Map<String, Object>> lambdaCapturingTypes = (List<Map<String, Object>>) entries.get("lambdaCapturingTypes");

        if (listNullOrEmpty(types) && listNullOrEmpty(lambdaCapturingTypes)) {
            System.out.println("ERROR: empty serialization-config detected: " + file.toURI());
            return true;
        }

        if (types != null) {
            // check include entries
            containsErrors |= containsDuplicatedEntries(types, file);

            for (var entry : types) {
                containsErrors |= checkTypeReachable(entry, file);
                containsErrors |= containsEntriesNotFromLibrary(entry, file);
            }
        }

        if (lambdaCapturingTypes != null) {
            // check include entries
            containsErrors |= containsDuplicatedEntries(lambdaCapturingTypes, file);

            for (var entry : lambdaCapturingTypes) {
                containsErrors |= checkTypeReachable(entry, file);
                containsErrors |= containsEntriesNotFromLibrary(entry, file);
            }
        }

        return containsErrors;
    }

    private boolean serializationConfigFileContainsErrors(File file) throws FileNotFoundException {
        Scanner sc = new Scanner(file);
        return sc.nextLine().contains("[") ? checkOldSerializationConfig(file) : checkNewSerializationConfig(file);
    }

    private boolean proxyConfigFileContainsErrors(File file) {
        List<Map<String, Object>> entries = getConfigEntries(file);

        if (entries.isEmpty()) {
            System.out.println("ERROR: empty proxy-config detected: " + file.toURI());
            return true;
        }

        boolean containsErrors = containsDuplicatedEntries(entries, file);
        for (var entry : entries) {
            containsErrors |= checkTypeReachable(entry, file);
            containsErrors |= containsEntriesNotFromLibrary(entry, file);
        }

        return containsErrors;
    }

    private boolean jniConfigFileContainsErrors(File file) {
        List<Map<String, Object>> entries = getConfigEntries(file);

        if (entries.isEmpty()) {
            System.out.println("ERROR: empty jni-config detected: " + file.toURI());
            return true;
        }

        boolean containsErrors = containsDuplicatedEntries(entries, file);
        for (var entry : entries) {
            containsErrors |= checkTypeReachable(entry, file);
            containsErrors |= containsEntriesNotFromLibrary(entry, file);
        }

        return containsErrors;
    }

    private boolean containsDuplicatedEntries(List<Map<String, Object>> entries, File file) {
        Map<Map<String, Object>, Integer> duplicates = new HashMap<>();
        entries.forEach(entry -> duplicates.merge(entry, 1, Integer::sum));

        // print all entries that appears more than once
        boolean containsDuplicates = false;
        for (var entry : duplicates.entrySet()) {
            if (entry.getValue() > 1) {
                String entryName = getEntryName(entry.getKey());
                if (entryName == null) {
                    entryName = entry.getKey().toString();
                }

                containsDuplicates = true;
                System.out.println("ERROR: In file " + file.toURI() + " there is a duplicated entry " + entryName);
            }
        }

        return containsDuplicates;
    }

    private boolean checkTypeReachable(Map<String, Object> entry, File file) {
        String typeReachable = getEntryTypeReachable(entry);
        String entryName = getEntryName(entry);

        // check if condition entry exists
        if (typeReachable == null) {
            System.out.println("ERROR: In file " + file.toURI() + " there is an entry " + entry + " with missing condition field.");
            return true;
        }

        // check if both entryName and typeReachable are from PREDEFINED_ALLOWED_PACKAGES since there are some cases where this is allowed
        if (entryName != null && PREDEFINED_ALLOWED_PACKAGES.stream().anyMatch(typeReachable::contains) && PREDEFINED_ALLOWED_PACKAGES.stream().anyMatch(entryName::contains)) {
            return false;
        }

        // check if typeReachable exists inside condition entry
        if (ILLEGAL_TYPE_VALUES.stream().anyMatch(typeReachable::startsWith)) {
            // get name of entry that misses typeReachable. If name cannot be determinate, write whole entry
            if (entryName == null) {
                entryName = entry.toString();
            }

            System.out.println("ERROR: In file " + file.toURI() + " entry: " + entryName + " contains illegal typeReachable value. Field" +
                    " typeReachable cannot be any of the following values: " + ILLEGAL_TYPE_VALUES);
            return true;
        }

        return false;
    }

    private boolean containsEntriesNotFromLibrary(Map<String, Object> entry, File file) {
        String typeReachable = getEntryTypeReachable(entry);
        String entryName = getEntryName(entry);

        // this is not a valid situation since every entry must have typeReachable
        if (typeReachable == null) {
            return true;
        }

        // valid case is when both typeReachable and entryName are from ALLOWED_PACKAGES
        if (entryName != null && PREDEFINED_ALLOWED_PACKAGES.stream().anyMatch(typeReachable::contains) && PREDEFINED_ALLOWED_PACKAGES.stream().anyMatch(entryName::contains)) {
            return false;
        }

        // if typeReachable is not from allowedPackages we have to report an error
        if (this.allowedPackages.stream().noneMatch(typeReachable::contains)) {
            System.out.println("ERROR: In file " + file.toURI() + "\n" +
                    "Entry: " + entryName + "\n" +
                    "TypeReachable: " + typeReachable + "\n" +
                    "doesn't belong to any of the specified packages: " + this.allowedPackages + "\n");
            return true;
        }

        return false;
    }

    @SuppressWarnings("unchecked")
    private String getEntryTypeReachable(Map<String, Object> entry) {
        Map<String, Object> condition = (Map<String, Object>) entry.get("condition");

        // check if condition entry exists
        if (condition == null) {
            return null;
        }

        return (String) condition.get("typeReachable");
    }

    private String getEntryName(Map<String, Object> entry) {
        return (String) entry.get("name");
    }

    private boolean listNullOrEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }

    @SuppressWarnings("unchecked")
    private List<String> getAllowedPackages() {
        File indexFile = getIndexFile().get().getAsFile();
        String groupId = coordinates.group();
        String artifactId = coordinates.artifact();

        List<Map<String, Object>> entries = getConfigEntries(indexFile);

        for (var entry : entries) {
            if (entry.get("module").toString().startsWith(groupId + ":" + artifactId)) {
                if (entry.get("allowed-packages") == null) {
                    throw new IllegalStateException("Missing allowed-packages property for " + groupId + ":" + artifactId);
                }

                return (List<String>) entry.get("allowed-packages");
            }
        }

        throw new IllegalStateException("Missing library name in: " + indexFile.toURI() + " for coordinates " + coordinates);
    }
}
