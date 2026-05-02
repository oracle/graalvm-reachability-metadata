/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package classworlds.classworlds;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.codehaus.classworlds.ClassWorld;
import org.codehaus.classworlds.Launcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class LauncherTest {
    private static final String MAIN_REALM = "test";
    private static final String CLASSWORLDS_CONF = "classworlds.conf";
    private static final String UBERJAR_CONF = "WORLDS-INF/conf/classworlds.conf";

    @BeforeEach
    void resetEntryPoints() {
        EnhancedEntryPoint.reset();
        StandardEntryPoint.reset();
    }

    @Test
    void launchInvokesEnhancedMainMethod() throws Exception {
        Launcher launcher = newConfiguredLauncher(EnhancedEntryPoint.class.getName());

        launcher.launch(new String[] { "alpha", "beta" });

        assertThat(EnhancedEntryPoint.arguments).containsExactly("alpha", "beta");
        assertThat(EnhancedEntryPoint.world).isSameAs(launcher.getWorld());
        assertThat(launcher.getExitCode()).isEqualTo(42);
        assertThat(StandardEntryPoint.arguments).isNull();
    }

    @Test
    void launchFallsBackToStandardMainMethod() throws Exception {
        Launcher launcher = newConfiguredLauncher(StandardEntryPoint.class.getName());

        launcher.launch(new String[] { "gamma" });

        assertThat(StandardEntryPoint.arguments).containsExactly("gamma");
        assertThat(launcher.getExitCode()).isEqualTo(7);
        assertThat(EnhancedEntryPoint.arguments).isNull();
    }

    @Test
    void mainWithExitCodeLoadsClasspathConfigurationResource() throws Exception {
        int exitCode = runWithConfigurationResource(CLASSWORLDS_CONF, false);

        assertThat(exitCode).isEqualTo(7);
        assertThat(StandardEntryPoint.arguments).containsExactly("from-resource");
    }

    @Test
    void mainWithExitCodeLoadsBootstrappedConfigurationResource() throws Exception {
        int exitCode = runWithConfigurationResource(UBERJAR_CONF, true);

        assertThat(exitCode).isEqualTo(7);
        assertThat(StandardEntryPoint.arguments).containsExactly("from-resource");
    }

    private static Launcher newConfiguredLauncher(String mainClassName) throws Exception {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        ClassWorld world = new ClassWorld(MAIN_REALM, classLoader);
        Launcher launcher = new Launcher();
        launcher.setWorld(world);
        launcher.setSystemClassLoader(classLoader);
        launcher.setAppMain(mainClassName, MAIN_REALM);
        return launcher;
    }

    private static int runWithConfigurationResource(String resourceName, boolean bootstrapped) throws Exception {
        String previousConfig = System.getProperty(CLASSWORLDS_CONF);
        String previousBootstrapped = System.getProperty("classworlds.bootstrapped");
        ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader resourceClassLoader = new ConfigurationResourceClassLoader(
                previousClassLoader,
                resourceName,
                launcherConfiguration(StandardEntryPoint.class.getName()));

        try {
            System.clearProperty(CLASSWORLDS_CONF);
            if (bootstrapped) {
                System.setProperty("classworlds.bootstrapped", "true");
            } else {
                System.clearProperty("classworlds.bootstrapped");
            }
            Thread.currentThread().setContextClassLoader(resourceClassLoader);

            return Launcher.mainWithExitCode(new String[] { "from-resource" });
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

    public static class EnhancedEntryPoint {
        static String[] arguments;
        static ClassWorld world;

        public static int main(String[] args, ClassWorld classWorld) {
            arguments = args;
            world = classWorld;
            return 42;
        }

        static void reset() {
            arguments = null;
            world = null;
        }
    }

    public static class StandardEntryPoint {
        static String[] arguments;

        public static int main(String[] args) {
            arguments = args;
            return 7;
        }

        static void reset() {
            arguments = null;
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
