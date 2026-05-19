/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_java;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.codehaus.plexus.languages.java.jpms.MainClassModuleNameExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class MainClassModuleNameExtractorTest {
    private static final String MODULE_NAME = "org.example.plexus.fixture";

    @TempDir
    Path temporaryDirectory;

    @Test
    void getsAutomaticModuleNameFromJarManifest() throws Exception {
        Path moduleJar = createAutomaticModuleJar();
        MainClassModuleNameExtractor extractor =
                new MainClassModuleNameExtractor(Path.of(System.getProperty("java.home")));

        Map<Path, String> moduleNames = extractor.extract(Map.of(moduleJar, moduleJar));

        assertThat(moduleNames).containsEntry(moduleJar, MODULE_NAME);
    }

    @Test
    void extractsModuleNamesThroughForkedMainClass() throws Exception {
        Path moduleJar = createAutomaticModuleJar();
        MainClassModuleNameExtractor extractor =
                new MainClassModuleNameExtractor(Path.of(System.getProperty("java.home")));

        Map<String, String> moduleNames = extractor.extract(Map.of("fixture", moduleJar));

        assertThat(moduleNames).containsEntry("fixture", MODULE_NAME);
    }

    private Path createAutomaticModuleJar() throws IOException {
        Path moduleJar = Files.createTempFile(temporaryDirectory, "automatic-module", ".jar");
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.putValue("Automatic-Module-Name", MODULE_NAME);

        try (OutputStream outputStream = Files.newOutputStream(moduleJar);
                JarOutputStream jarOutputStream = new JarOutputStream(outputStream, manifest)) {
            JarEntry resourceEntry = new JarEntry("org/example/plexus/fixture/resource.txt");
            jarOutputStream.putNextEntry(resourceEntry);
            jarOutputStream.write("fixture".getBytes(StandardCharsets.UTF_8));
            jarOutputStream.closeEntry();
        }
        return moduleJar;
    }
}
