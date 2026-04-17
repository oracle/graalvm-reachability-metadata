/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.sun.activation.registries;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class MimeTypeFile {
    private final Map<String, String> mimeTypes = new HashMap<>();

    public MimeTypeFile() {
    }

    public MimeTypeFile(String fileName) throws IOException {
        load(Path.of(fileName));
    }

    public MimeTypeFile(InputStream inputStream) throws IOException {
        load(inputStream);
    }

    public void appendToRegistry(String mimeTypesDefinition) {
        try {
            load(new ByteArrayInputStream(mimeTypesDefinition.getBytes(StandardCharsets.UTF_8)));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to parse MIME types definition", exception);
        }
    }

    public String getMIMETypeString(String extension) {
        return mimeTypes.get(extension);
    }

    private void load(Path path) throws IOException {
        try (InputStream inputStream = Files.newInputStream(path)) {
            load(inputStream);
        }
    }

    private void load(InputStream inputStream) throws IOException {
        InputStreamReader inputStreamReader = new InputStreamReader(
            inputStream,
            StandardCharsets.UTF_8
        );
        try (BufferedReader reader = new BufferedReader(inputStreamReader)) {
            String line;
            while ((line = reader.readLine()) != null) {
                parseLine(line);
            }
        }
    }

    private void parseLine(String line) {
        String trimmedLine = line.trim();
        if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
            return;
        }

        String[] tokens = trimmedLine.split("\\s+");
        if (tokens.length < 2) {
            return;
        }

        String mimeType = tokens[0];
        for (int index = 1; index < tokens.length; index++) {
            mimeTypes.put(tokens[index], mimeType);
        }
    }
}
