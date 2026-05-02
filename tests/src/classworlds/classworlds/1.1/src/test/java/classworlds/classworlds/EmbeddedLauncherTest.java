/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package classworlds.classworlds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.codehaus.classworlds.ClassWorld;
import org.codehaus.classworlds.EmbeddedLauncher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class EmbeddedLauncherTest {
    private static final String MAIN_REALM = "test";
    private static final String CLASSWORLDS_CONF = "classworlds.conf";
    private static final String UBERJAR_CONF = "WORLDS-INF/conf/classworlds.conf";

    @BeforeEach
    void resetEntryPoint() {
        EmbeddedEntryPoint.reset();
    }

    @Test
    void launchLoadsClasspathConfigurationResourceAndDiscoversEnhancedExecuteMethod() {
        assertThatThrownBy(() -> runWithConfigurationResource(CLASSWORLDS_CONF, false))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(EmbeddedEntryPoint.world).isNull();
    }

    @Test
    void launchLoadsBootstrappedConfigurationResourceAndDiscoversEnhancedExecuteMethod() {
        assertThatThrownBy(() -> runWithConfigurationResource(UBERJAR_CONF, true))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(EmbeddedEntryPoint.world).isNull();
    }

    private static void runWithConfigurationResource(String resourceName, boolean bootstrapped) throws Exception {
        String previousConfig = System.getProperty(CLASSWORLDS_CONF);
        String previousBootstrapped = System.getProperty("classworlds.bootstrapped");
        ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader resourceClassLoader = new ConfigurationResourceClassLoader(
                previousClassLoader,
                resourceName,
                launcherConfiguration(EmbeddedEntryPoint.class.getName()));
        EmbeddedLauncher launcher = new EmbeddedLauncher();
        launcher.setSystemClassLoader(resourceClassLoader);

        try {
            System.clearProperty(CLASSWORLDS_CONF);
            if (bootstrapped) {
                System.setProperty("classworlds.bootstrapped", "true");
            } else {
                System.clearProperty("classworlds.bootstrapped");
            }
            Thread.currentThread().setContextClassLoader(resourceClassLoader);

            launcher.launch();
        } finally {
            restoreProperty(CLASSWORLDS_CONF, previousConfig);
            restoreProperty("classworlds.bootstrapped", previousBootstrapped);
            Thread.currentThread().setContextClassLoader(previousClassLoader);
        }
    }

    private static String launcherConfiguration(String mainClassName) {
        return """
                main is %s from %s
                [%s]
                """.formatted(mainClassName, MAIN_REALM, MAIN_REALM);
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    public static class EmbeddedEntryPoint {
        static String[] arguments;
        static ClassWorld world;

        public static void execute(String[] args, ClassWorld classWorld) {
            arguments = args;
            world = classWorld;
        }

        static void reset() {
            arguments = null;
            world = null;
        }
    }

    private static class ConfigurationResourceClassLoader extends ClassLoader {
        private final String resourceName;
        private final byte[] configuration;

        ConfigurationResourceClassLoader(ClassLoader parent, String resourceName, String configuration) {
            super(parent);
            this.resourceName = resourceName;
            this.configuration = configuration.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (resourceName.equals(name)) {
                return new ByteArrayInputStream(configuration);
            }
            return super.getResourceAsStream(name);
        }
    }
}
