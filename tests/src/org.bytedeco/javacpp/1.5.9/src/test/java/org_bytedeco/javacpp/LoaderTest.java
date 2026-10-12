/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bytedeco.javacpp;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.zip.Deflater;

import org.junit.jupiter.api.Test;

import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.annotation.Platform;
import org.bytedeco.javacpp.presets.javacpp;

@Platform(executable = "missing-javacpp-test-command")
public class LoaderTest {

    public static class ExecutableBinding {}

    @Test
    public void loadsPlatformAndMavenResources() throws Exception {
        Properties platform = Loader.loadProperties(Loader.getPlatform(), null);
        Properties fallback = Loader.loadProperties("missing-javacpp-test-platform", "generic");
        URL directResource = Loader.findResource(
                Loader.class, "/META-INF/maven/org.bytedeco/javacpp/pom.properties");
        URL[] searchedResources = Loader.findResources(
                LoaderTest.class, "META-INF/maven/org.bytedeco/javacpp/pom.properties");

        assertThat(platform.getProperty("platform")).isEqualTo(Loader.getPlatform());
        assertThat(fallback.getProperty("platform.compiler")).isEqualTo("g++");
        assertThat(Loader.getVersion("org.bytedeco", "javacpp", Loader.class)).isNotBlank();
        assertThat(directResource).isNotNull();
        assertThat(searchedResources).isNotEmpty();
    }

    @Test
    public void loadsPackagedNativeRuntimeAndRegistersPointerSize() {
        String loadedLibrary = Loader.load(javacpp.class);

        assertThat(loadedLibrary).isNotBlank();
        assertThat(Loader.sizeof(Pointer.class)).isPositive();
    }

    @Test
    public void loadsSystemLibraryForRequestedClassLoaderScope() throws Exception {
        Path zipLibrary = findJdkLibrary("zip");
        String loadedLibrary = Loader.loadLibrary(Deflater.class, new URL[0], "zip");
        String loadedFromUrl = Loader.loadLibrary(
                Deflater.class, new URL[] {zipLibrary.toUri().toURL()}, "zip#zip-explicit");
        Deflater deflater = new Deflater();
        try {
            byte[] input = "JavaCPP native loading".getBytes(StandardCharsets.UTF_8);
            byte[] compressed = new byte[64];
            deflater.setInput(input);
            deflater.finish();

            assertThat(loadedLibrary).isEqualTo("zip");
            assertThat(loadedFromUrl).isIn(zipLibrary.toString(), "zip");
            assertThat(deflater.deflate(compressed)).isPositive();
            assertThat(deflater.finished()).isTrue();
        } finally {
            deflater.end();
        }
    }

    @Test
    public void initializesBindingBeforeLookingForItsExecutable() {
        String oldLowerCase = System.getProperty("org.bytedeco.javacpp.loadlibraries");
        String oldCamelCase = System.getProperty("org.bytedeco.javacpp.loadLibraries");
        try {
            System.setProperty("org.bytedeco.javacpp.loadlibraries", "true");
            System.setProperty("org.bytedeco.javacpp.loadLibraries", "true");

            String executable = Loader.load(
                    ExecutableBinding.class, Loader.loadProperties(), false, null);

            assertThat(executable).isNull();
        } finally {
            restoreProperty("org.bytedeco.javacpp.loadlibraries", oldLowerCase);
            restoreProperty("org.bytedeco.javacpp.loadLibraries", oldCamelCase);
        }
    }

    private static Path findJdkLibrary(String name) {
        Path javaHome = Paths.get(System.getProperty("java.home"));
        String mappedName = System.mapLibraryName(name);
        Path[] candidates = {
            javaHome.resolve("lib").resolve(mappedName),
            javaHome.resolve("bin").resolve(mappedName)
        };
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }
        throw new IllegalStateException("JDK library not found: " + mappedName);
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
