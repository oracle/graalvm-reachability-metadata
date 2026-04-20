/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.sun.activation.registries;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MailcapFile {
    private static final String JAVA_COMMAND_PREFIX = "x-java-";
    private static final String FALLBACK_ENTRY = "x-java-fallback-entry=true";

    private final Map<String, Map<String, List<String>>> preferredMailcaps;
    private final Map<String, Map<String, List<String>>> fallbackMailcaps;

    public MailcapFile() {
        this.preferredMailcaps = new LinkedHashMap<>();
        this.fallbackMailcaps = new LinkedHashMap<>();
    }

    public MailcapFile(String fileName) throws IOException {
        this();
        try (InputStream inputStream = Files.newInputStream(Path.of(fileName))) {
            parse(inputStream);
        }
    }

    public MailcapFile(InputStream inputStream) throws IOException {
        this();
        parse(inputStream);
    }

    public void appendToMailcap(String mailcap) {
        parseLine(mailcap);
    }

    public Map<String, List<String>> getMailcapList(String mimeType) {
        return preferredMailcaps.get(normalizeMimeType(mimeType));
    }

    public Map<String, List<String>> getMailcapFallbackList(String mimeType) {
        return fallbackMailcaps.get(normalizeMimeType(mimeType));
    }

    public String[] getMimeTypes() {
        Set<String> mimeTypes = new LinkedHashSet<>();
        mimeTypes.addAll(preferredMailcaps.keySet());
        mimeTypes.addAll(fallbackMailcaps.keySet());
        return mimeTypes.toArray(String[]::new);
    }

    public String[] getNativeCommands(String mimeType) {
        return new String[0];
    }

    private void parse(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
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

        String[] parts = trimmedLine.split(";");
        if (parts.length == 0) {
            return;
        }

        String mimeType = normalizeMimeType(parts[0]);
        if (mimeType == null || mimeType.isEmpty()) {
            return;
        }

        boolean fallback = false;
        Map<String, List<String>> commands = new LinkedHashMap<>();
        for (int i = 1; i < parts.length; i++) {
            String attribute = parts[i].trim();
            if (attribute.isEmpty()) {
                continue;
            }
            if (FALLBACK_ENTRY.equalsIgnoreCase(attribute)) {
                fallback = true;
                continue;
            }
            if (!attribute.regionMatches(true, 0, JAVA_COMMAND_PREFIX, 0, JAVA_COMMAND_PREFIX.length())) {
                continue;
            }

            int equalsIndex = attribute.indexOf('=');
            if (equalsIndex < 0 || equalsIndex == attribute.length() - 1) {
                continue;
            }

            String verb = attribute.substring(JAVA_COMMAND_PREFIX.length(), equalsIndex).trim();
            String className = attribute.substring(equalsIndex + 1).trim();
            if (verb.isEmpty() || className.isEmpty()) {
                continue;
            }
            commands.computeIfAbsent(verb, key -> new ArrayList<>()).add(className);
        }

        if (commands.isEmpty()) {
            return;
        }

        Map<String, Map<String, List<String>>> target = fallback ? fallbackMailcaps : preferredMailcaps;
        Map<String, List<String>> existingCommands = target.computeIfAbsent(mimeType, key -> new LinkedHashMap<>());
        for (Map.Entry<String, List<String>> entry : commands.entrySet()) {
            existingCommands.computeIfAbsent(entry.getKey(), key -> new ArrayList<>()).addAll(entry.getValue());
        }
    }

    private String normalizeMimeType(String mimeType) {
        if (mimeType == null) {
            return null;
        }
        return mimeType.trim().toLowerCase(Locale.ROOT);
    }
}
