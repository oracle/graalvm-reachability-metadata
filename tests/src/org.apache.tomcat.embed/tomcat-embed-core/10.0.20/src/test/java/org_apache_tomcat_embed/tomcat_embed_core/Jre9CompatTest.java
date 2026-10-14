/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.apache.tomcat.util.compat.JreCompat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class Jre9CompatTest {

    @Test
    void exposesModuleAndMultiReleaseJarCompatibility(@TempDir Path directory) throws Exception {
        JreCompat compatibility = JreCompat.getInstance();
        File jar = createMultiReleaseJar(directory);
        Deque<URL> modulePath = new ArrayDeque<>();

        compatibility.disableCachingForJarUrlConnections();
        compatibility.addBootModulePath(modulePath);
        try (JarFile jarFile = compatibility.jarFileNewInstance(jar)) {
            assertThat(compatibility.jarFileIsMultiRelease(jarFile)).isTrue();
            assertThat(compatibility.jarFileRuntimeMajorVersion()).isPositive();
        }

        Method length = String.class.getMethod("length");
        assertThat(compatibility.canAccess("tomcat", length)).isTrue();
        assertThat(compatibility.isExported(String.class)).isTrue();
        assertThat(compatibility.getModuleName(String.class)).isEqualTo("java.base");
        assertThat(modulePath).isNotEmpty();
    }

    private static File createMultiReleaseJar(Path directory) throws Exception {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.putValue("Multi-Release", "true");
        Path jar = directory.resolve("multi-release.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            // The manifest alone is sufficient for JarFile's multi-release check.
        }
        return jar.toFile();
    }
}
