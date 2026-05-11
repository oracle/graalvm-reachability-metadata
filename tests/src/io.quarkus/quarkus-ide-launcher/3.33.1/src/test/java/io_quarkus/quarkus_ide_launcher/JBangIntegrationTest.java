/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_ide_launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;

import io.quarkus.bootstrap.jbang.JBangBuilderImpl;
import io.quarkus.launcher.JBangIntegration;
import io.quarkus.launcher.RuntimeLaunchClassLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class JBangIntegrationTest {
    private static final String SERIALIZED_APP_MODEL = "quarkus-internal.serialized-app-model.path";

    @TempDir
    Path temporaryDirectory;

    @Test
    void postBuildLoadsAndInvokesJbangBuilderImplementation() throws IOException {
        Path appClasses = Files.createDirectories(temporaryDirectory.resolve("classes"));
        Path pomFile = temporaryDirectory.resolve("pom.xml");
        Files.writeString(pomFile, "<project/>\n");
        Path asmDependencyPath = temporaryDirectory.resolve("asm.jar");
        Path applicationDependencyPath = temporaryDirectory.resolve("application.jar");

        List<Map.Entry<String, String>> repositories = List.of(entry(
                "central",
                "https://repo.maven.apache.org/maven2"));
        List<Map.Entry<String, Path>> originalDeps = List.of(
                entry("org.ow2.asm:asm:9.7", asmDependencyPath),
                entry("org.example:application:1.0", applicationDependencyPath));
        List<String> comments = List.of(
                "plain comment",
                JBangIntegration.CONFIG + " quarkus.http.port=0",
                JBangIntegration.CONFIG + " quarkus.log.category.test.level=DEBUG");

        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        String originalQuarkusDev = System.getProperty("quarkus.dev");
        String originalSerializedAppModel = System.getProperty(SERIALIZED_APP_MODEL);
        JBangBuilderImpl.reset();

        try {
            System.clearProperty("quarkus.dev");
            System.setProperty(SERIALIZED_APP_MODEL, "serialized-model");

            Map<String, Object> result = JBangIntegration.postBuild(
                    appClasses,
                    pomFile,
                    repositories,
                    originalDeps,
                    comments,
                    true);

            assertThat(result).containsEntry("status", "ok");
            assertThat(result).containsEntry("native-image", true);
            assertThat(JBangBuilderImpl.invocationCount()).isEqualTo(1);
            assertThat(JBangBuilderImpl.appClasses()).isEqualTo(appClasses);
            assertThat(JBangBuilderImpl.pomFile()).isEqualTo(pomFile);
            assertThat(JBangBuilderImpl.repositories()).containsExactlyElementsOf(repositories);
            assertThat(JBangBuilderImpl.dependencies()).containsExactly(entry(
                    "org.example:application:1.0",
                    applicationDependencyPath));
            assertThat(JBangBuilderImpl.configurationProperties())
                    .containsEntry("quarkus.http.port", "0")
                    .containsEntry("quarkus.log.category.test.level", "DEBUG");
            assertThat(JBangBuilderImpl.nativeImage()).isTrue();
            assertThat(JBangBuilderImpl.contextClassLoader()).isInstanceOf(RuntimeLaunchClassLoader.class);
            assertThat(System.getProperty(SERIALIZED_APP_MODEL)).isNull();
            assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(originalClassLoader);
        } finally {
            restoreProperty("quarkus.dev", originalQuarkusDev);
            restoreProperty(SERIALIZED_APP_MODEL, originalSerializedAppModel);
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
