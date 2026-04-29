/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_modules.jboss_modules;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.jboss.modules.IterableResourceLoader;
import org.jboss.modules.Resource;
import org.jboss.modules.ResourceLoaders;
import org.jboss.modules.Version;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Jboss_modulesTest {
    @Test
    void versionsParseAndCompareNumericAndQualifiedParts() {
        Version betaVersion = Version.parse("1.0.0.Beta1");
        Version finalVersion = Version.parse("1.0.0.Final");
        Version nextMinorVersion = Version.parse("1.1.0");

        assertTrue(finalVersion.compareTo(betaVersion) > 0);
        assertTrue(nextMinorVersion.compareTo(finalVersion) > 0);
        assertEquals(Version.parse("1.0.0.Final"), finalVersion);
        assertEquals("1.0.0.Final", finalVersion.toString());
    }

    @Test
    void pathResourceLoaderReadsAndIteratesFileSystemResources(@TempDir Path resourceRoot) throws Exception {
        Path configDirectory = Files.createDirectories(resourceRoot.resolve("config"));
        Path nestedDirectory = Files.createDirectories(configDirectory.resolve("nested"));
        Files.writeString(configDirectory.resolve("settings.properties"), "feature.enabled=true\n", UTF_8);
        Files.writeString(nestedDirectory.resolve("details.txt"), "nested-resource\n", UTF_8);

        IterableResourceLoader resourceLoader = ResourceLoaders.createPathResourceLoader(resourceRoot);

        Resource settingsResource = resourceLoader.getResource("config/settings.properties");
        assertNotNull(settingsResource);
        assertEquals("config/settings.properties", settingsResource.getName());
        assertEquals("feature.enabled=true\n".getBytes(UTF_8).length, settingsResource.getSize());
        assertEquals("feature.enabled=true\n", readResource(settingsResource));
        assertNull(resourceLoader.getResource("config/missing.properties"));

        assertIterableEquals(
                List.of("config/settings.properties"),
                resourceNames(resourceLoader.iterateResources("config", false)));
        assertIterableEquals(
                List.of("config/nested/details.txt", "config/settings.properties"),
                resourceNames(resourceLoader.iterateResources("config", true)));
        assertTrue(resourceLoader.getPaths().contains("config/nested"));
    }

    private static String readResource(Resource resource) throws IOException {
        try (InputStream inputStream = resource.openStream()) {
            return new String(inputStream.readAllBytes(), UTF_8);
        }
    }

    private static List<String> resourceNames(Iterator<Resource> resources) {
        List<String> names = new ArrayList<>();
        while (resources.hasNext()) {
            names.add(resources.next().getName());
        }
        names.sort(String::compareTo);
        return names;
    }
}
