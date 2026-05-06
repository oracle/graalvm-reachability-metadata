/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package classworlds.classworlds;

import static org.assertj.core.api.Assertions.assertThat;

import org.codehaus.classworlds.uberjar.boot.Bootstrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.SAME_THREAD)
public class BootstrapperTest {
    @Test
    void bootstrapLoadsLauncherClassDiscoversMainMethodAndInvokesIt() throws Exception {
        BootstrapApplication.reset();
        RecordingClassLoader classLoader = new RecordingClassLoader();
        String previousBootstrapped = System.getProperty("classworlds.bootstrapped");
        String previousLibraryPath = System.getProperty("classworlds.lib");
        try {
            TestBootstrapper bootstrapper = new TestBootstrapper(new String[] {"alpha", "beta"}, classLoader);
            System.clearProperty("classworlds.bootstrapped");

            bootstrapper.bootstrap();

            assertThat(classLoader.loadedClassName).isEqualTo(Bootstrapper.LAUNCHER_CLASS_NAME);
            assertThat(System.getProperty("classworlds.bootstrapped")).isEqualTo("true");
            assertThat(BootstrapApplication.arguments).containsExactly("alpha", "beta");
        } finally {
            setOrClearProperty("classworlds.bootstrapped", previousBootstrapped);
            setOrClearProperty("classworlds.lib", previousLibraryPath);
        }
    }

    private static void setOrClearProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    public static final class BootstrapApplication {
        private static String[] arguments = new String[0];

        private BootstrapApplication() {
        }

        public static void main(String[] args) {
            arguments = args.clone();
        }

        private static void reset() {
            arguments = new String[0];
        }
    }

    private static final class TestBootstrapper extends Bootstrapper {
        private final ClassLoader classLoader;

        private TestBootstrapper(String[] args, ClassLoader classLoader) throws Exception {
            super(args);
            this.classLoader = classLoader;
        }

        @Override
        protected ClassLoader getInitialClassLoader() {
            return classLoader;
        }
    }

    private static final class RecordingClassLoader extends ClassLoader {
        private String loadedClassName;

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            loadedClassName = name;
            if (Bootstrapper.LAUNCHER_CLASS_NAME.equals(name)) {
                return BootstrapApplication.class;
            }
            return super.loadClass(name);
        }
    }
}
