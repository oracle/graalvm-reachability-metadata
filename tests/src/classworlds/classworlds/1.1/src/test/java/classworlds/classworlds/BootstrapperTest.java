/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package classworlds.classworlds;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.codehaus.classworlds.uberjar.boot.Bootstrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
public class BootstrapperTest {
    private static final String BOOTSTRAPPED_PROPERTY = "classworlds.bootstrapped";
    private static final String CLASSWORLDS_LIB_PROPERTY = "classworlds.lib";

    @Test
    void bootstrapLoadsLauncherClassAndInvokesPublicStaticMainMethod() throws Exception {
        String originalBootstrapped = System.getProperty(BOOTSTRAPPED_PROPERTY);
        String originalClassworldsLib = System.getProperty(CLASSWORLDS_LIB_PROPERTY);
        RecordingClassLoader classLoader = new RecordingClassLoader(BootstrapperTest.class.getClassLoader());
        BootstrapTarget.reset();
        try {
            Bootstrapper bootstrapper = new TestBootstrapper(new String[] {"alpha", "beta"}, classLoader);

            bootstrapper.bootstrap();

            assertThat(classLoader.getLoadedClassNames()).containsExactly(Bootstrapper.LAUNCHER_CLASS_NAME);
            assertThat(System.getProperty(BOOTSTRAPPED_PROPERTY)).isEqualTo("true");
            assertThat(BootstrapTarget.args).containsExactly("alpha", "beta");
        } finally {
            BootstrapTarget.reset();
            restoreProperty(BOOTSTRAPPED_PROPERTY, originalBootstrapped);
            restoreProperty(CLASSWORLDS_LIB_PROPERTY, originalClassworldsLib);
        }
    }

    private static void restoreProperty(String property, String value) {
        if (value == null) {
            System.clearProperty(property);
        } else {
            System.setProperty(property, value);
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
        private final List<String> loadedClassNames = new ArrayList<>();

        private RecordingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            loadedClassNames.add(name);
            if (Bootstrapper.LAUNCHER_CLASS_NAME.equals(name)) {
                return BootstrapTarget.class;
            }
            return super.loadClass(name);
        }

        private List<String> getLoadedClassNames() {
            return Collections.unmodifiableList(loadedClassNames);
        }
    }

    public static class BootstrapTarget {
        private static String[] args;

        public static void main(String[] arguments) {
            args = arguments.clone();
        }

        private static void reset() {
            args = null;
        }
    }
}
