package org.graalvm.internal.tck;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.graalvm.internal.tck.model.MetadataVersionsIndexEntry;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.util.internal.VersionNumber;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;

public abstract class TestedVersionUpdaterTask extends DefaultTask {

    @Option(option = "coordinates", description = "GAV coordinates of the library")
    void extractInformationFromCoordinates(String c) {
        String[] coordinatesParts = c.split(":");
        if (coordinatesParts.length != 3) {
            throw new IllegalArgumentException("Maven coordinates should have 3 parts");
        }
        String group = coordinatesParts[0];
        String artifact = coordinatesParts[1];
        String version = coordinatesParts[2];
        Coordinates coordinates = new Coordinates(group, artifact, version);

        getIndexFile().set(getProject().file(CoordinateUtils.replace("metadata/$group$/$artifact$/index.json", coordinates)));
        getNewVersion().set(version);
    }

    @Input
    @Option(option = "lastSupportedVersion", description = "Last version of the library that passed tests")
    protected abstract Property<String> getLastSupportedVersion();

    @Input
    protected abstract Property<String> getNewVersion();

    @OutputFiles
    protected abstract RegularFileProperty getIndexFile();

    @TaskAction
    void run() throws IllegalStateException, IOException {
        File coordinatesMetadataIndex = getIndexFile().get().getAsFile();
        ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).setSerializationInclusion(JsonInclude.Include.NON_NULL);

        List<MetadataVersionsIndexEntry> entries = objectMapper.readValue(coordinatesMetadataIndex, new TypeReference<>() {});
        for (MetadataVersionsIndexEntry entry : entries) {
            if (entry.testedVersions().contains(getLastSupportedVersion().get())) {
                entry.testedVersions().add(getNewVersion().get());
                entry.testedVersions().sort(Comparator.comparing(VersionNumber::parse));
            }
        }

        DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter();
        prettyPrinter.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);

        // Ensure the JSON file ends with a trailing EOL
        String json = objectMapper.writer(prettyPrinter).writeValueAsString(entries);
        if (!json.endsWith("\n")) {
            json = json + System.lineSeparator();
        }
        Files.writeString(coordinatesMetadataIndex.toPath(), json, java.nio.charset.StandardCharsets.UTF_8);
    }
}
