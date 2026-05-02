/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package classworlds.classworlds;

import static org.assertj.core.api.Assertions.assertThat;

import org.codehaus.classworlds.uberjar.boot.Bootstrapper;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BootstrapperTest {
    @BeforeEach
    void resetEntryPoint() {
        BootstrapEntryPoint.reset();
    }

    @Test
    void bootstrapLoadsLauncherClassAndInvokesPublicStaticMainMethod() throws Exception {
        String previousBootstrapped = System.getProperty("classworlds.bootstrapped");
        String previousLibraryDirectory = System.getProperty("classworlds.lib");

        try {
            Bootstrapper bootstrapper = new BootstrapperProbe(new String[] { "alpha", "beta" });

            bootstrapper.bootstrap();

            assertThat(BootstrapEntryPoint.arguments).containsExactly("alpha", "beta");
            assertThat(System.getProperty("classworlds.bootstrapped")).isEqualTo("true");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } finally {
            restoreProperty("classworlds.bootstrapped", previousBootstrapped);
            restoreProperty("classworlds.lib", previousLibraryDirectory);
        }
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    public static class BootstrapEntryPoint {
        static String[] arguments;

        public static void main(String[] args) {
            arguments = args;
        }

        static void reset() {
            arguments = null;
        }
    }

    private static final class BootstrapperProbe extends Bootstrapper {
        private final ClassLoader launcherClassLoader = new LauncherClassLoader();

        BootstrapperProbe(String[] args) throws Exception {
            super(args);
        }

        @Override
        protected ClassLoader getInitialClassLoader() {
            return launcherClassLoader;
        }
    }

    private static final class LauncherClassLoader extends ClassLoader {
        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (Bootstrapper.LAUNCHER_CLASS_NAME.equals(name)) {
                return BootstrapEntryPoint.class;
            }
            return super.loadClass(name);
        }
    }
}
