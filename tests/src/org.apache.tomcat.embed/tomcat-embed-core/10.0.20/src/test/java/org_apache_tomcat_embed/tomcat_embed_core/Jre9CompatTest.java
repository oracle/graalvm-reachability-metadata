/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.apache.tomcat.util.compat.JreCompat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Jre9CompatTest {

    @Test
    void usesJreCompatibilityOperationsForModulesAndMultiReleaseJars() throws Exception {
        JreCompat compat = JreCompat.getInstance();
        Path jarPath = Files.createTempFile("tomcat-jre-compat", ".jar");
        try {
            try (JarOutputStream ignored = new JarOutputStream(Files.newOutputStream(jarPath))) {
                // The empty archive is sufficient for the compatibility API.
            }

            try (JarFile jar = compat.jarFileNewInstance(jarPath.toString())) {
                assertThat(compat.jarFileIsMultiRelease(jar)).isFalse();
            }

            Deque<URL> moduleUrls = new ArrayDeque<>();
            compat.addBootModulePath(moduleUrls);
            compat.disableCachingForJarUrlConnections();

            Method length = String.class.getMethod("length");
            assertThat(compat.canAccess("value", length)).isTrue();
            assertThat(compat.isExported(String.class)).isTrue();
            assertThat(compat.getModuleName(String.class)).isEqualTo("java.base");
        } finally {
            Files.deleteIfExists(jarPath);
        }
    }
}
