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
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.codehaus.classworlds.ClassRealm;
import org.codehaus.classworlds.ClassWorld;
import org.codehaus.classworlds.EmbeddedLauncher;
import org.codehaus.classworlds.NoSuchRealmException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
@ResourceLock("contextClassLoader")
public class EmbeddedLauncherTest {
    private static final String REALM_ID = "test";
    private static final String CLASSWORLDS_CONF_PROPERTY = "classworlds.conf";
    private static final String BOOTSTRAPPED_PROPERTY = "classworlds.bootstrapped";
    private static final String CLASSWORLDS_CONF_RESOURCE = "classworlds.conf";
    private static final String UBERJAR_CONF_RESOURCE = "WORLDS-INF/conf/classworlds.conf";

    @Test
    void findsEnhancedExecuteMethodOnConfiguredMainClass() throws Exception {
        InspectableEmbeddedLauncher launcher = configuredLauncher(EmbeddedEntryPoint.class);

        Method method = launcher.findEnhancedMainMethod();

        assertThat(method.getName()).isEqualTo("execute");
        assertThat(method.getReturnType()).isEqualTo(Void.TYPE);
        assertThat(method.getParameterTypes()).containsExactly(String[].class, ClassWorld.class);
    }

    @Test
    void launchLoadsClasspathConfigurationResource() throws Exception {
        ResourceConfigurationClassLoader resourceClassLoader = new ResourceConfigurationClassLoader(
                EmbeddedLauncherTest.class.getClassLoader(),
                CLASSWORLDS_CONF_RESOURCE,
                configurationFor(NoExecuteEntryPoint.class));

        withLauncherResourceLookup(false, resourceClassLoader, () -> {
            EmbeddedLauncher launcher = newLauncher();

            assertThatThrownBy(launcher::launch).isInstanceOf(NoSuchMethodException.class)
                    .hasMessageContaining("execute");
        });

        assertThat(resourceClassLoader.getRequestedResources()).containsExactly(CLASSWORLDS_CONF_RESOURCE);
    }

    @Test
    void launchLoadsBootstrappedUberjarConfigurationResource() throws Exception {
        ResourceConfigurationClassLoader resourceClassLoader = new ResourceConfigurationClassLoader(
                EmbeddedLauncherTest.class.getClassLoader(),
                UBERJAR_CONF_RESOURCE,
                configurationFor(NoExecuteEntryPoint.class));

        withLauncherResourceLookup(true, resourceClassLoader, () -> {
            EmbeddedLauncher launcher = newLauncher();

            assertThatThrownBy(launcher::launch).isInstanceOf(NoSuchMethodException.class)
                    .hasMessageContaining("execute");
        });

        assertThat(resourceClassLoader.getRequestedResources()).containsExactly(UBERJAR_CONF_RESOURCE);
    }

    private static EmbeddedLauncher newLauncher() {
        EmbeddedLauncher launcher = new EmbeddedLauncher();
        launcher.setSystemClassLoader(EmbeddedLauncherTest.class.getClassLoader());
        return launcher;
    }

    private static InspectableEmbeddedLauncher configuredLauncher(Class<?> mainClass) throws Exception {
        ClassLoader applicationClassLoader = EmbeddedLauncherTest.class.getClassLoader();
        ClassWorld world = new ClassWorld();
        ClassRealm realm = world.newRealm(REALM_ID, applicationClassLoader);
        InspectableEmbeddedLauncher launcher = new InspectableEmbeddedLauncher();
        launcher.setSystemClassLoader(applicationClassLoader);
        launcher.setWorld(world);
        launcher.setAppMain(mainClass.getName(), realm.getId());
        return launcher;
    }

    private static String configurationFor(Class<?> mainClass) {
        return """
                main is %s from %s

                [%s]
                """.formatted(mainClass.getName(), REALM_ID, REALM_ID);
    }

    private static void withLauncherResourceLookup(
            boolean bootstrapped, ClassLoader resourceClassLoader, ThrowingRunnable runnable) throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        String originalConf = System.getProperty(CLASSWORLDS_CONF_PROPERTY);
        String originalBootstrapped = System.getProperty(BOOTSTRAPPED_PROPERTY);
        try {
            Thread.currentThread().setContextClassLoader(resourceClassLoader);
            System.clearProperty(CLASSWORLDS_CONF_PROPERTY);
            if (bootstrapped) {
                System.setProperty(BOOTSTRAPPED_PROPERTY, "true");
            } else {
                System.clearProperty(BOOTSTRAPPED_PROPERTY);
            }
            runnable.run();
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
            restoreProperty(CLASSWORLDS_CONF_PROPERTY, originalConf);
            restoreProperty(BOOTSTRAPPED_PROPERTY, originalBootstrapped);
        }
    }

    private static void restoreProperty(String property, String value) {
        if (value == null) {
            System.clearProperty(property);
        } else {
            System.setProperty(property, value);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class InspectableEmbeddedLauncher extends EmbeddedLauncher {
        private Method findEnhancedMainMethod()
                throws ClassNotFoundException, NoSuchMethodException, NoSuchRealmException {
            return getEnhancedMainMethod();
        }
    }

    private static final class ResourceConfigurationClassLoader extends ClassLoader {
        private final String resourceName;
        private final byte[] configuration;
        private final List<String> requestedResources = new ArrayList<>();

        private ResourceConfigurationClassLoader(ClassLoader parent, String resourceName, String configuration) {
            super(parent);
            this.resourceName = resourceName;
            this.configuration = configuration.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            requestedResources.add(name);
            if (resourceName.equals(name)) {
                return new ByteArrayInputStream(configuration);
            }
            return super.getResourceAsStream(name);
        }

        private List<String> getRequestedResources() {
            return Collections.unmodifiableList(requestedResources);
        }
    }

    public static class EmbeddedEntryPoint {
        public static void execute(String[] newArguments, ClassWorld newWorld) {
            if (newArguments == null || newWorld == null) {
                throw new IllegalArgumentException("Embedded entry point arguments are required");
            }
        }
    }

    public static class NoExecuteEntryPoint {
    }
}
