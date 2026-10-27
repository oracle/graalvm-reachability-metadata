/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib_nodep;

import net.sf.cglib.transform.AbstractClassTransformer;
import net.sf.cglib.transform.ClassFilter;
import net.sf.cglib.transform.ClassTransformer;
import net.sf.cglib.transform.ClassTransformerFactory;
import net.sf.cglib.transform.TransformingClassLoader;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractClassLoaderTest {
    @Test
    void loadsDelegatedAndTransformedClasses() throws ClassNotFoundException {
        try {
            TransformingClassLoader loader = new TransformingClassLoader(
                    getClass().getClassLoader(),
                    new CglibClassFilter(),
                    new NoOpClassTransformerFactory()
            );

            assertThat(loader.loadClass(String.class.getName())).isEqualTo(String.class);

            Class<?> transformedClass = loader.loadClass("net.sf.cglib.core.ClassGenerator");

            assertThat(transformedClass.getClassLoader()).isEqualTo(loader);
            assertThat(transformedClass.getName()).isEqualTo("net.sf.cglib.core.ClassGenerator");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static final class CglibClassFilter implements ClassFilter {
        @Override
        public boolean accept(String className) {
            return className.startsWith("net.sf.cglib.");
        }
    }

    private static final class NoOpClassTransformerFactory implements ClassTransformerFactory {
        @Override
        public ClassTransformer newInstance() {
            return new NoOpClassTransformer();
        }
    }

    private static final class NoOpClassTransformer extends AbstractClassTransformer {
    }
}
