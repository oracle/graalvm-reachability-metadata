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
import java.util.ArrayList;
import java.util.List;

import org.codehaus.classworlds.ClassWorld;
import org.codehaus.classworlds.EmbeddedLauncher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.SAME_THREAD)
public class EmbeddedLauncherTest {
    private static final String REALM_NAME = "app";

    @Test
    void launchLoadsConfigurationFromClasspathResourceAndDiscoversExecuteMethod() throws Exception {
        ResourceBackedClassLoader classLoader = new ResourceBackedClassLoader(
                EmbeddedLauncherTest.class.getClassLoader(),
                "classworlds.conf",
                configurationFor(EmbeddedApplication.class));
        EmbeddedLauncher launcher = new EmbeddedLauncher();
        launcher.setSystemClassLoader(classLoader);

        // EmbeddedLauncher 1.1 discovers this signature but invokes it with only the ClassWorld.
        assertThatThrownBy(() -> withLauncherContext(classLoader, null, launcher::launch))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(classLoader.requestedResources).containsExactly("classworlds.conf");
        assertThat(launcher.getMainClassName()).isEqualTo(EmbeddedApplication.class.getName());
        assertThat(launcher.getMainRealmName()).isEqualTo(REALM_NAME);
        assertThat(EmbeddedApplication.executed).isFalse();
    }

    @Test
    void launchLoadsBootstrappedConfigurationResourceAndDiscoversExecuteMethod() throws Exception {
        ResourceBackedClassLoader classLoader = new ResourceBackedClassLoader(
                EmbeddedLauncherTest.class.getClassLoader(),
                "WORLDS-INF/conf/classworlds.conf",
                configurationFor(EmbeddedApplication.class));
        EmbeddedLauncher launcher = new EmbeddedLauncher();
        launcher.setSystemClassLoader(classLoader);

        // EmbeddedLauncher 1.1 discovers this signature but invokes it with only the ClassWorld.
        assertThatThrownBy(() -> withLauncherContext(classLoader, "true", launcher::launch))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(classLoader.requestedResources).containsExactly("WORLDS-INF/conf/classworlds.conf");
        assertThat(launcher.getMainClassName()).isEqualTo(EmbeddedApplication.class.getName());
        assertThat(launcher.getMainRealmName()).isEqualTo(REALM_NAME);
        assertThat(EmbeddedApplication.executed).isFalse();
    }

    private static String configurationFor(Class<?> applicationClass) {
        return """
                main is %s from %s
                [%s]
                """.formatted(applicationClass.getName(), REALM_NAME, REALM_NAME);
    }

    private static void withLauncherContext(
            ClassLoader contextClassLoader,
            String bootstrapped,
            ThrowingRunnable action) throws Exception {
        ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
        String previousConfiguration = System.getProperty("classworlds.conf");
        String previousBootstrapped = System.getProperty("classworlds.bootstrapped");
        EmbeddedApplication.reset();
        Thread.currentThread().setContextClassLoader(contextClassLoader);
        System.clearProperty("classworlds.conf");
        setOrClearProperty("classworlds.bootstrapped", bootstrapped);
        try {
            action.run();
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
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    public static final class EmbeddedApplication {
        private static boolean executed;

        private EmbeddedApplication() {
        }

        public static void execute(String[] args, ClassWorld classWorld) {
            executed = true;
        }

        private static void reset() {
            executed = false;
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
