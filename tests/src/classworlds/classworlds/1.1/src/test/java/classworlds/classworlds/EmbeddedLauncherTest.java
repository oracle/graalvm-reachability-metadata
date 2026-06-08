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
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.codehaus.classworlds.ClassRealm;
import org.codehaus.classworlds.ClassWorld;
import org.codehaus.classworlds.EmbeddedLauncher;
import org.codehaus.classworlds.NoSuchRealmException;
import org.junit.jupiter.api.Test;

public class EmbeddedLauncherTest {
    private static final String REALM_ID = "test";
    private static final String CLASSWORLDS_CONF = "classworlds.conf";
    private static final String UBERJAR_CONF = "WORLDS-INF/conf/classworlds.conf";
    private static final String CLASSWORLDS_CONF_PROPERTY = "classworlds.conf";
    private static final String BOOTSTRAPPED_PROPERTY = "classworlds.bootstrapped";

    @Test
    void getEnhancedMainMethodFindsPublicExecuteMethodFromMainRealm() throws Exception {
        ExposedEmbeddedLauncher launcher = configuredLauncher(TwoArgumentEntrypoint.class);

        Method method = launcher.findEnhancedMainMethod();

        assertThat(method.getName()).isEqualTo("execute");
        assertThat(method.getParameterTypes()).containsExactly(String[].class, ClassWorld.class);
        assertThat(method.getReturnType()).isEqualTo(Void.TYPE);
    }

    @Test
    void launchXInvokesProtectedEnhancedMainMethodImplementation() throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        OneArgumentEntrypoint.reset();
        ExposedEmbeddedLauncher launcher = configuredLauncher(OneArgumentEntrypoint.class);
        launcher.setMainMethod(OneArgumentEntrypoint.class.getMethod("execute", ClassWorld.class));
        try {
            launcher.launchEnhancedEntrypoint();

            assertThat(OneArgumentEntrypoint.world).isSameAs(launcher.getWorld());
            assertThat(Thread.currentThread().getContextClassLoader())
                    .isSameAs(launcher.getMainRealm().getClassLoader());
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void launchLoadsClasspathConfigurationResource() throws Exception {
        RecordingEmbeddedLauncher launcher = new RecordingEmbeddedLauncher();
        RecordingResourceClassLoader classLoader = new RecordingResourceClassLoader(
                EmbeddedLauncherTest.class.getClassLoader(),
                CLASSWORLDS_CONF,
                configurationFor(ResourceEntrypoint.class));
        launcher.setSystemClassLoader(classLoader);

        withLauncherResourceLookup(classLoader, false, launcher::launch);

        assertThat(classLoader.requests).contains(CLASSWORLDS_CONF);
        assertThat(launcher.launched).isTrue();
        assertThat(launcher.getMainClassName()).isEqualTo(ResourceEntrypoint.class.getName());
        assertThat(launcher.getMainRealmName()).isEqualTo(REALM_ID);
    }

    @Test
    void launchLoadsBootstrappedUberjarConfigurationResource() throws Exception {
        RecordingEmbeddedLauncher launcher = new RecordingEmbeddedLauncher();
        RecordingResourceClassLoader classLoader = new RecordingResourceClassLoader(
                EmbeddedLauncherTest.class.getClassLoader(),
                UBERJAR_CONF,
                configurationFor(ResourceEntrypoint.class));
        launcher.setSystemClassLoader(classLoader);

        withLauncherResourceLookup(classLoader, true, launcher::launch);

        assertThat(classLoader.requests).contains(UBERJAR_CONF);
        assertThat(launcher.launched).isTrue();
        assertThat(launcher.getMainClassName()).isEqualTo(ResourceEntrypoint.class.getName());
        assertThat(launcher.getMainRealmName()).isEqualTo(REALM_ID);
    }

    private static ExposedEmbeddedLauncher configuredLauncher(Class<?> mainClass) throws Exception {
        ClassLoader classLoader = EmbeddedLauncherTest.class.getClassLoader();
        ClassWorld world = new ClassWorld();
        ClassRealm realm = world.newRealm(REALM_ID, classLoader);
        ExposedEmbeddedLauncher launcher = new ExposedEmbeddedLauncher();
        launcher.setSystemClassLoader(classLoader);
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
            ClassLoader classLoader,
            boolean bootstrapped,
            ThrowingRunnable runnable) throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        String originalConf = System.getProperty(CLASSWORLDS_CONF_PROPERTY);
        String originalBootstrapped = System.getProperty(BOOTSTRAPPED_PROPERTY);
        try {
            Thread.currentThread().setContextClassLoader(classLoader);
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

    public static class TwoArgumentEntrypoint {
        public static void execute(String[] arguments, ClassWorld classWorld) {
        }
    }

    public static class OneArgumentEntrypoint {
        private static ClassWorld world;

        public static void execute(ClassWorld classWorld) {
            world = classWorld;
        }

        private static void reset() {
            world = null;
        }
    }

    public static class ResourceEntrypoint {
    }

    private static class ExposedEmbeddedLauncher extends EmbeddedLauncher {
        private Method mainMethod;

        Method findEnhancedMainMethod() throws Exception {
            return getEnhancedMainMethod();
        }

        void setMainMethod(Method method) {
            mainMethod = method;
        }

        void launchEnhancedEntrypoint() throws Exception {
            launchX();
        }

        @Override
        protected Method getEnhancedMainMethod()
                throws ClassNotFoundException, NoSuchMethodException, NoSuchRealmException {
            if (mainMethod != null) {
                return mainMethod;
            }
            return super.getEnhancedMainMethod();
        }
    }

    private static final class RecordingEmbeddedLauncher extends EmbeddedLauncher {
        private boolean launched;

        @Override
        protected void launchX() {
            launched = true;
        }
    }

    private static final class RecordingResourceClassLoader extends ClassLoader {
        private final String resourceName;
        private final byte[] resourceContent;
        private final List<String> requests = new ArrayList<>();

        private RecordingResourceClassLoader(
                ClassLoader parent, String resourceName, String resourceContent) {
            super(parent);
            this.resourceName = resourceName;
            this.resourceContent = resourceContent.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            requests.add(name);
            if (resourceName.equals(name)) {
                return new ByteArrayInputStream(resourceContent);
            }
            return super.getResourceAsStream(name);
        }
    }
}
