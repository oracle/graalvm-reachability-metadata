/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_classworlds;

import static org.assertj.core.api.Assertions.assertThat;

import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.launcher.Launcher;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.junit.jupiter.api.Test;

public class LauncherTest {
    private static final String REALM_ID = "test";
    private static final String CLASSWORLDS_CONF_PROPERTY = "classworlds.conf";
    private static final String BOOTSTRAPPED_PROPERTY = "classworlds.bootstrapped";

    @Test
    void launchInvokesEnhancedMainMethodFromConfiguredRealm() throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        EnhancedMain.reset();
        Launcher launcher = configuredLauncher(EnhancedMain.class);
        try {
            launcher.launch(new String[] {"alpha", "beta"});

            assertThat(EnhancedMain.args).containsExactly("alpha", "beta");
            assertThat(EnhancedMain.world).isSameAs(launcher.getWorld());
            assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(launcher.getMainRealm());
            assertThat(launcher.getExitCode()).isEqualTo(21);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void launchFallsBackToStandardMainMethodFromConfiguredRealm() throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        StandardMain.reset();
        Launcher launcher = configuredLauncher(StandardMain.class);
        try {
            launcher.launch(new String[] {"gamma"});

            assertThat(StandardMain.args).containsExactly("gamma");
            assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(launcher.getMainRealm());
            assertThat(launcher.getExitCode()).isEqualTo(34);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void mainWithExitCodeLoadsClasspathConfigurationResource() throws Exception {
        StandardResourceMain.reset();
        withLauncherResourceLookup(false, () -> {
            int exitCode = Launcher.mainWithExitCode(new String[] {"from-classpath"});

            assertThat(exitCode).isEqualTo(55);
            assertThat(StandardResourceMain.args).containsExactly("from-classpath");
        });
    }

    @Test
    void mainWithExitCodeLoadsBootstrappedUberjarConfigurationResource() throws Exception {
        EnhancedResourceMain.reset();
        withLauncherResourceLookup(true, () -> {
            int exitCode = Launcher.mainWithExitCode(new String[] {"from-uberjar"});

            assertThat(exitCode).isEqualTo(89);
            assertThat(EnhancedResourceMain.args).containsExactly("from-uberjar");
            assertThat(EnhancedResourceMain.world).isNotNull();
        });
    }

    private static Launcher configuredLauncher(Class<?> mainClass) throws Exception {
        ClassLoader classLoader = LauncherTest.class.getClassLoader();
        ClassWorld world = new ClassWorld();
        ClassRealm realm = world.newRealm(REALM_ID, classLoader);
        Launcher launcher = new Launcher();
        launcher.setSystemClassLoader(classLoader);
        launcher.setWorld(world);
        launcher.setAppMain(mainClass.getName(), realm.getId());
        return launcher;
    }

    private static void withLauncherResourceLookup(boolean bootstrapped, ThrowingRunnable runnable) throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        String originalConf = System.getProperty(CLASSWORLDS_CONF_PROPERTY);
        String originalBootstrapped = System.getProperty(BOOTSTRAPPED_PROPERTY);
        try {
            Thread.currentThread().setContextClassLoader(LauncherTest.class.getClassLoader());
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

    public static class EnhancedMain {
        private static String[] args;
        private static ClassWorld world;

        public static int main(String[] arguments, ClassWorld classWorld) {
            args = arguments.clone();
            world = classWorld;
            return 21;
        }

        private static void reset() {
            args = null;
            world = null;
        }
    }

    public static class StandardMain {
        private static String[] args;

        public static int main(String[] arguments) {
            args = arguments.clone();
            return 34;
        }

        private static void reset() {
            args = null;
        }
    }

    public static class StandardResourceMain {
        private static String[] args;

        public static int main(String[] arguments) {
            args = arguments.clone();
            return 55;
        }

        private static void reset() {
            args = null;
        }
    }

    public static class EnhancedResourceMain {
        private static String[] args;
        private static ClassWorld world;

        public static int main(String[] arguments, ClassWorld classWorld) {
            args = arguments.clone();
            world = classWorld;
            return 89;
        }

        private static void reset() {
            args = null;
            world = null;
        }
    }
}
