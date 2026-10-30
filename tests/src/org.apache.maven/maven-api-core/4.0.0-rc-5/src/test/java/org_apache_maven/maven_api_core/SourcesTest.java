/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.api.services.ModelSource;
import org.apache.maven.api.services.Source;
import org.apache.maven.api.services.Sources;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SourcesTest {
    @TempDir
    Path directory;

    @Test
    void readsFileContentThroughFreshStreams() throws IOException {
        Path file = directory.resolve("settings.xml");
        String content = """
                <settings>
                    <offline>true</offline>
                </settings>
                """;
        Files.writeString(file, content);
        Source source = Sources.fromPath(file);

        assertEquals(file, source.getPath());
        assertEquals(file.toString(), source.getLocation());
        for (int i = 0; i < 2; i++) {
            try (InputStream stream = source.openStream()) {
                assertEquals(content, new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        Source resolved = Sources.fromPath(directory).resolve("settings.xml");
        assertEquals(source, resolved);
        assertEquals(source.hashCode(), resolved.hashCode());
    }

    @Test
    void resolvesRelatedBuildModelsWithUserSuppliedLocator() throws IOException {
        Path parent = directory.resolve("pom.xml");
        Path module = Files.createDirectory(directory.resolve("module"));
        Path child = Files.writeString(module.resolve("pom.xml"), "<project/>");
        Files.writeString(parent, "<project><modules><module>module</module></modules></project>");
        ModelSource source = Sources.buildSource(parent);
        ModelSource.ModelLocator locator = project -> {
            Path candidate = Files.isDirectory(project) ? project.resolve("pom.xml") : project;
            return Files.isRegularFile(candidate) ? candidate : null;
        };

        ModelSource related = source.resolve(locator, "module");
        assertEquals(parent, source.getPath());
        assertEquals(child, related.getPath());
        try (InputStream stream = related.openStream()) {
            assertEquals("<project/>", new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertNull(source.resolve(locator, "absent-module"));
    }

    @Test
    void readsResolvedModelWithoutExposingLocalBuildPaths() throws IOException {
        Path file = Files.writeString(directory.resolve("downloaded-pom.xml"), "<project/>");
        ModelSource source = Sources.resolvedSource(file, "repository:example:application");

        assertEquals("repository:example:application", source.getLocation());
        assertNull(source.getPath());
        assertNull(source.resolve("relative-pom.xml"));
        assertNull(source.resolve(project -> project, "relative-pom.xml"));
        try (InputStream stream = source.openStream()) {
            assertEquals("<project/>", new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertEquals(file.toString(), Sources.resolvedSource(file, null).getLocation());
    }
}
