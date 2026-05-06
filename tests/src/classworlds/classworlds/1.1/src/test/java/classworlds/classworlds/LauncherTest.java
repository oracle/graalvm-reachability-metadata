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
import java.util.ArrayList;
import java.util.List;

import org.codehaus.classworlds.ClassWorld;
import org.codehaus.classworlds.Launcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.SAME_THREAD)
public class LauncherTest {
    private static final String REALM_NAME = "app";

    @Test
    void launchInvokesEnhancedMainMethod() throws Exception {
        EnhancedApplication.reset();
        Launcher launcher = configuredLauncher(EnhancedApplication.class.getName());

        launcher.launch(new String[] {"alpha", "beta"});

        assertThat(launcher.getExitCode()).isEqualTo(17);
        assertThat(EnhancedApplication.arguments).containsExactly("alpha", "beta");
        assertThat(EnhancedApplication.world).isSameAs(launcher.getWorld());
    }

    @Test
    void launchFallsBackToStandardMainMethod() throws Exception {
        StandardApplication.reset();
        Launcher launcher = configuredLauncher(StandardApplication.class.getName());

        launcher.launch(new String[] {"gamma"});

        assertThat(launcher.getExitCode()).isEqualTo(23);
        assertThat(StandardApplication.arguments).containsExactly("gamma");
    }

    @Test
    void mainWithExitCodeLoadsConfigurationFromClasspathResource() throws Exception {
        ResourceBackedClassLoader classLoader = new ResourceBackedClassLoader(
                LauncherTest.class.getClassLoader(),
                "classworlds.conf",
                configurationFor(EnhancedApplication.class));

        int exitCode = withLauncherContext(classLoader, null, () -> Launcher.mainWithExitCode(new String[] {"plain"}));

        assertThat(exitCode).isEqualTo(17);
        assertThat(classLoader.requestedResources).containsExactly("classworlds.conf");
        assertThat(EnhancedApplication.arguments).containsExactly("plain");
    }

    @Test
    void mainWithExitCodeLoadsBootstrappedConfigurationResource() throws Exception {
        ResourceBackedClassLoader classLoader = new ResourceBackedClassLoader(
                LauncherTest.class.getClassLoader(),
                "WORLDS-INF/conf/classworlds.conf",
                configurationFor(EnhancedApplication.class));

        int exitCode = withLauncherContext(classLoader, "true", () -> Launcher.mainWithExitCode(new String[] {"boot"}));

        assertThat(exitCode).isEqualTo(17);
        assertThat(classLoader.requestedResources).containsExactly("WORLDS-INF/conf/classworlds.conf");
        assertThat(EnhancedApplication.arguments).containsExactly("boot");
    }

    private static Launcher configuredLauncher(String mainClassName) throws Exception {
        ClassWorld world = new ClassWorld();
        world.newRealm(REALM_NAME, LauncherTest.class.getClassLoader());

        Launcher launcher = new Launcher();
        launcher.setWorld(world);
        launcher.setAppMain(mainClassName, REALM_NAME);
        return launcher;
    }

    private static String configurationFor(Class<?> applicationClass) {
        return """
                main is %s from %s
                [%s]
                """.formatted(applicationClass.getName(), REALM_NAME, REALM_NAME);
    }

    private static int withLauncherContext(
            ClassLoader contextClassLoader,
            String bootstrapped,
            ThrowingIntSupplier action) throws Exception {
        ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
        String previousConfiguration = System.getProperty("classworlds.conf");
        String previousBootstrapped = System.getProperty("classworlds.bootstrapped");
        EnhancedApplication.reset();
        Thread.currentThread().setContextClassLoader(contextClassLoader);
        System.clearProperty("classworlds.conf");
        setOrClearProperty("classworlds.bootstrapped", bootstrapped);
        try {
            return action.getAsInt();
        } finally {
            Thread.currentThread().setContextClassLoader(previousClassLoader);
            setOrClearProperty("classworlds.conf", previousConfiguration);
            setOrClearProperty("classworlds.bootstrapped", previousBootstrapped);
        }
    }

    private static void setOrClearProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    @FunctionalInterface
    private interface ThrowingIntSupplier {
        int getAsInt() throws Exception;
    }

    public static final class EnhancedApplication {
        private static List<String> arguments = List.of();
        private static ClassWorld world;

        private EnhancedApplication() {
        }

        public static int main(String[] args, ClassWorld classWorld) {
            arguments = List.of(args);
            world = classWorld;
            return 17;
        }

        private static void reset() {
            arguments = List.of();
            world = null;
        }
    }

    public static final class StandardApplication {
        private static List<String> arguments = List.of();

        private StandardApplication() {
        }

        public static int main(String[] args) {
            arguments = List.of(args);
            return 23;
        }

        private static void reset() {
            arguments = List.of();
        }
    }

    private static final class ResourceBackedClassLoader extends ClassLoader {
        private final String resourceName;
        private final byte[] resourceContent;
        private final List<String> requestedResources = new ArrayList<>();

        private ResourceBackedClassLoader(ClassLoader parent, String resourceName, String resourceContent) {
            super(parent);
            this.resourceName = resourceName;
            this.resourceContent = resourceContent.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            requestedResources.add(name);
            if (resourceName.equals(name)) {
                return new ByteArrayInputStream(resourceContent);
            }
            return super.getResourceAsStream(name);
        }
    }
}
