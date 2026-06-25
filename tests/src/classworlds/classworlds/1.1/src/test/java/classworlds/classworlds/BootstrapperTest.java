/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package classworlds.classworlds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.codehaus.classworlds.uberjar.boot.Bootstrapper;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
public class BootstrapperTest {
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
