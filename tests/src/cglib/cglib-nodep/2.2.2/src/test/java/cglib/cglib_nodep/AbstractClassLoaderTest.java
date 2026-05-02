/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib_nodep;

import static org.assertj.core.api.Assertions.assertThat;

import net.sf.cglib.transform.AbstractClassLoader;
import net.sf.cglib.transform.ClassFilter;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class AbstractClassLoaderTest {
    @Test
    void delegatesRejectedClassesToParentLoader() throws ClassNotFoundException {
        ResourceClassLoader loader = new ResourceClassLoader(
                AbstractClassLoaderTest.class.getClassLoader(),
                new ExactNameFilter(ClassFilter.class.getName()));

        Class<?> loadedClass = loader.loadClass(String.class.getName());

        assertThat(loadedClass).isSameAs(String.class);
    }

    @Test
    void loadsAcceptedClassBytesFromClasspathResource() throws ClassNotFoundException {
        ResourceClassLoader loader = new ResourceClassLoader(
                AbstractClassLoaderTest.class.getClassLoader(),
                new ExactNameFilter(ClassFilter.class.getName()));

        try {
            Class<?> loadedClass = loader.loadClass(ClassFilter.class.getName());
            Class<?> secondLookup = loader.loadClass(ClassFilter.class.getName());

            assertThat(loadedClass.getName()).isEqualTo(ClassFilter.class.getName());
            assertThat(loadedClass.getClassLoader()).isSameAs(loader);
            assertThat(secondLookup).isSameAs(loadedClass);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static final class ResourceClassLoader extends AbstractClassLoader {
        ResourceClassLoader(ClassLoader classPath, ClassFilter filter) {
            super(AbstractClassLoaderTest.class.getClassLoader(), classPath, filter);
        }
    }

    private static final class ExactNameFilter implements ClassFilter {
        private final String acceptedName;

        ExactNameFilter(String acceptedName) {
            this.acceptedName = acceptedName;
        }

        public boolean accept(String className) {
            return acceptedName.equals(className);
        }
    }
}
