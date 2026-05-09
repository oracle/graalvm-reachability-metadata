/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_configuration.commons_configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.apache.commons.configuration.BaseConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationUtils;
import org.junit.jupiter.api.Test;

public class ConfigurationUtilsTest {
    private static final String CLASSPATH_RESOURCE = "commons-configuration/configuration-utils-resource.properties";

    @Test
    public void cloneConfigurationInvokesPublicCloneMethod() {
        BaseConfiguration original = new BaseConfiguration();
        original.addProperty("message", "copied");

        Configuration cloned = ConfigurationUtils.cloneConfiguration(original);

        assertThat(cloned).isNotSameAs(original);
        assertThat(cloned.getString("message")).isEqualTo("copied");
    }

    @Test
    public void getUrlConvertsAbsoluteFileThroughUriReflection() throws Exception {
        Path file = Files.createTempFile("configuration-utils", ".properties");
        String originalJavaVersion = useLegacyJavaVersionString();
        try {
            URL url = ConfigurationUtils.getURL(null, file.toString());

            assertThat(url).isEqualTo(file.toUri().toURL());
        } finally {
            restoreJavaVersionString(originalJavaVersion);
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void locateConvertsAbsoluteFileThroughUriReflection() throws Exception {
        Path file = Files.createTempFile("configuration-utils-locate", ".properties");
        String originalJavaVersion = useLegacyJavaVersionString();
        try {
            URL url = ConfigurationUtils.locate(null, file.toString());

            assertThat(url).isEqualTo(file.toUri().toURL());
        } finally {
            restoreJavaVersionString(originalJavaVersion);
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void locateLoadsResourceFromContextClassLoader() throws Exception {
        Path file = Files.createTempFile("configuration-utils-context", ".properties");
        URL expectedUrl = file.toUri().toURL();
        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(new SingleResourceClassLoader(CLASSPATH_RESOURCE, expectedUrl));
        try {
            URL url = ConfigurationUtils.locate(null, CLASSPATH_RESOURCE);

            assertThat(url).isEqualTo(expectedUrl);
        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void locateLoadsResourceFromSystemClassLoaderWhenContextClassLoaderIsUnavailable() throws Exception {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(null);
        try {
            URL url = ConfigurationUtils.locate(null, CLASSPATH_RESOURCE);
            assertThat(url).isNotNull();

            Properties properties = new Properties();
            try (InputStream input = url.openStream()) {
                properties.load(input);
            }
            assertThat(properties.getProperty("loaded")).isEqualTo("true");
        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
        }
    }

    private static String useLegacyJavaVersionString() {
        // Commons Lang 2.x only recognizes pre-Java-9 version strings in this compatibility check.
        String originalJavaVersion = System.getProperty("java.version");
        System.setProperty("java.version", "1.8.0");
        return originalJavaVersion;
    }

    private static void restoreJavaVersionString(String originalJavaVersion) {
        if (originalJavaVersion == null) {
            System.clearProperty("java.version");
        } else {
            System.setProperty("java.version", originalJavaVersion);
        }
    }

    private static final class SingleResourceClassLoader extends ClassLoader {
        private final String resourceName;
        private final URL resourceUrl;

        private SingleResourceClassLoader(String resourceName, URL resourceUrl) {
            super(null);
            this.resourceName = resourceName;
            this.resourceUrl = resourceUrl;
        }

        @Override
        public URL getResource(String name) {
            if (resourceName.equals(name)) {
                return resourceUrl;
            }
            return null;
        }
    }
}
