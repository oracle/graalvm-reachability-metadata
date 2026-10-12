/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bytedeco.javacpp;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacpp.annotation.Platform;

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

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
