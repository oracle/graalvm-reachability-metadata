/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_ide_launcher;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import io.quarkus.bootstrap.jbang.JBangBuilderImpl;
import io.quarkus.launcher.JBangIntegration;
import io.quarkus.launcher.RuntimeLaunchClassLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class JBangIntegrationAnonymous1Test {
    private static final String RESOURCE_PROBE = "quarkus-ide-launcher-resource-probe.txt";

    @TempDir
    Path temporaryDirectory;

    @Test
    void jbangFilteringClassLoaderDelegatesNonOrgResourceLookups() throws IOException {
        Path appClasses = Files.createDirectories(temporaryDirectory.resolve("classes"));
        Path pomFile = temporaryDirectory.resolve("pom.xml");
        Files.writeString(pomFile, "<project/>\n");
        Path applicationDependencyPath = temporaryDirectory.resolve("application.jar");

        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        String originalQuarkusDev = System.getProperty("quarkus.dev");
        JBangBuilderImpl.reset();

        try {
            System.clearProperty("quarkus.dev");

            JBangIntegration.postBuild(
                    appClasses,
                    pomFile,
                    List.of(),
                    List.of(entry("org.example:application:1.0", applicationDependencyPath)),
                    List.of(),
                    false);

            ClassLoader contextClassLoader = JBangBuilderImpl.contextClassLoader();
            assertThat(contextClassLoader).isInstanceOf(RuntimeLaunchClassLoader.class);

            URL resource = contextClassLoader.getResource(RESOURCE_PROBE);
            Enumeration<URL> resources = contextClassLoader.getResources(RESOURCE_PROBE);

            assertThat(resource).isNull();
            assertThat(resources).isNotNull();
            assertThat(resources.hasMoreElements()).isFalse();
        } finally {
            restoreProperty("quarkus.dev", originalQuarkusDev);
            Thread.currentThread().setContextClassLoader(originalClassLoader);
            JBangBuilderImpl.reset();
        }
    }

    private static <K, V> Map.Entry<K, V> entry(K key, V value) {
        return new AbstractMap.SimpleImmutableEntry<>(key, value);
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
