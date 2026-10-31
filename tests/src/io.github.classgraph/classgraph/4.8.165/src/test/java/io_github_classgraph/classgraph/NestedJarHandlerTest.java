/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_classgraph.classgraph;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NestedJarHandlerTest {
    @TempDir
    Path directory;

    @Test
    void readsResourcesFromCompressedNestedJar() throws Exception {
        Path inner = ScanSupport.archive(directory);
        Path outer = directory.resolve("application.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(outer))) {
            output.putNextEntry(new JarEntry("lib/catalog.jar"));
            Files.copy(inner, output);
            output.closeEntry();
        }
        try (ScanResult scan = new ClassGraph().overrideClasspath(outer + "!/lib/catalog.jar")
                .acceptPaths("catalog").setMaxBufferedJarRAMSize(1).scan(2)) {
            assertEquals(ScanSupport.CONTENT, scan.getResourcesWithPath(ScanSupport.PAYLOAD).get(0).getContentAsString());
        }
    }
}
