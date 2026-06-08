/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package classworlds.classworlds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.codehaus.classworlds.uberjar.boot.Bootstrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class BootstrapperTest {
    private static final String CLASSWORLDS_CONF_PROPERTY = "classworlds.conf";
    private static final String BOOTSTRAPPED_PROPERTY = "classworlds.bootstrapped";
    private static final String REALM_ID = "test";

    @TempDir
    private Path tempDir;

    @Test
    void bootstrapLoadsLauncherAndInvokesItsMainMethod() throws Exception {
        String[] args = {"alpha", "beta"};
        RecordingClassLoader launcherLoader = new RecordingClassLoader(BootstrapperTest.class.getClassLoader());
        Bootstrapper bootstrapper = new DelegatingBootstrapper(args, launcherLoader);
        Path configuration = writeConfiguration(RecordingMain.class);
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        String originalConf = System.getProperty(CLASSWORLDS_CONF_PROPERTY);
        String originalBootstrapped = System.getProperty(BOOTSTRAPPED_PROPERTY);
        RecordingMain.reset();
        try {
            Thread.currentThread().setContextClassLoader(BootstrapperTest.class.getClassLoader());
            System.setProperty(CLASSWORLDS_CONF_PROPERTY, configuration.toString());
            System.clearProperty(BOOTSTRAPPED_PROPERTY);

            assertThatExceptionOfType(InvocationTargetException.class)
                    .isThrownBy(bootstrapper::bootstrap)
                    .satisfies(exception -> assertThat(exception.getCause()).isInstanceOf(SentinelError.class));

            assertThat(launcherLoader.loadedClasses).contains(Bootstrapper.LAUNCHER_CLASS_NAME);
            assertThat(System.getProperty(BOOTSTRAPPED_PROPERTY)).isEqualTo("true");
            assertThat(RecordingMain.args).containsExactly(args);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
            restoreProperty(CLASSWORLDS_CONF_PROPERTY, originalConf);
            restoreProperty(BOOTSTRAPPED_PROPERTY, originalBootstrapped);
            RecordingMain.reset();
        }
    }

    private Path writeConfiguration(Class<?> mainClass) throws Exception {
        Path configuration = tempDir.resolve("classworlds.conf");
        Files.writeString(configuration, """
                main is %s from %s

                [%s]
                """.formatted(mainClass.getName(), REALM_ID, REALM_ID), StandardCharsets.UTF_8);
        return configuration;
    }

    private static void restoreProperty(String property, String value) {
        if (value == null) {
            System.clearProperty(property);
        } else {
            System.setProperty(property, value);
        }
    }

    public static class RecordingMain {
        private static String[] args;

        public static void main(String[] arguments) {
            args = arguments.clone();
            throw new SentinelError();
        }

        private static void reset() {
            args = null;
        }
    }

    public static class SentinelError extends Error {
        private static final long serialVersionUID = 1L;
    }

    private static final class DelegatingBootstrapper extends Bootstrapper {
        private final ClassLoader initialClassLoader;

        private DelegatingBootstrapper(String[] args, ClassLoader initialClassLoader) throws Exception {
            super(args);
            this.initialClassLoader = initialClassLoader;
        }

        @Override
        protected ClassLoader getInitialClassLoader() {
            return initialClassLoader;
        }
    }

    private static final class RecordingClassLoader extends ClassLoader {
        private final List<String> loadedClasses = new ArrayList<>();

        private RecordingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            loadedClasses.add(name);
            return super.loadClass(name);
        }
    }
}
