package org.graalvm.internal.tck;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.graalvm.internal.tck.model.MetadataVersionsIndexEntry;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.util.internal.VersionNumber;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public abstract class TestedVersionUpdaterTask extends DefaultTask {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @Option(option = "coordinates", description = "GAV coordinates of the library")
    void setCoordinates(String coordinates) {
        this.coordinates = coordinates;
    }

    @Option(option = "lastSupportedVersion", description = "Last version of the library that passed tests")
    void setLastSupportedVersion(String version) {
        this.lastSupportedVersion = version;
    }

    private String coordinates;
    private String lastSupportedVersion;


    @TaskAction
    void run() throws IllegalStateException, IOException {
        List<String> GAVCoordinates = Arrays.stream(coordinates.split(":")).toList();
        if (GAVCoordinates.size() != 3) {
            throw new IllegalArgumentException("Maven coordinates should have 3 parts");
        }

        String group = GAVCoordinates.get(0);
        String artifact = GAVCoordinates.get(1);
        String version = GAVCoordinates.get(2);
        Coordinates c = new Coordinates(group, artifact, version);
        addToMetadataIndexJson(c);
    }

    private void addToMetadataIndexJson(Coordinates c) throws IOException {
        File coordinatesMetadataIndex = getProject().file(CoordinateUtils.replace("metadata/$group$/$artifact$/index.json", c));
        List<MetadataVersionsIndexEntry> entries = objectMapper.readValue(coordinatesMetadataIndex, new TypeReference<>() {
        });

        for (MetadataVersionsIndexEntry entry : entries) {
            if (entry.testedVersions().contains(lastSupportedVersion)) {
                entry.testedVersions().add(c.version());
                entry.testedVersions().sort(Comparator.comparing(VersionNumber::parse));
            }
        }

        DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter();
        prettyPrinter.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);

        objectMapper.writer(prettyPrinter).writeValue(coordinatesMetadataIndex, entries);
    }
}
