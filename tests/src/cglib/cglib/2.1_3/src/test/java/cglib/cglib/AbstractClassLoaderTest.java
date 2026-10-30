/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib;

import java.util.ArrayList;
import java.util.List;

import net.sf.cglib.transform.TransformingClassLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class AbstractClassLoaderTest {
    @Test
    void reportsMissingClassResourceWithoutInvokingTransformer() {
        String className = "cglib.cglib.MissingTransformInput";
        ClassLoader parent = AbstractClassLoaderTest.class.getClassLoader();
        TransformingClassLoader loader = new TransformingClassLoader(
                parent, className::equals, () -> {
                    throw new AssertionError("Missing class resources must not be transformed");
                });

        assertThatExceptionOfType(ClassNotFoundException.class)
                .isThrownBy(() -> loader.loadClass(className))
                .withMessage(className);
    }

    @Test
    void delegatesExcludedClassesToParentWithoutTransformingThem() throws ClassNotFoundException {
        ClassLoader parent = AbstractClassLoaderTest.class.getClassLoader();
        List<String> filteredNames = new ArrayList<>();
        TransformingClassLoader loader = new TransformingClassLoader(parent, name -> {
            filteredNames.add(name);
            return false;
        }, () -> {
            throw new AssertionError("Excluded classes must not be transformed");
        });

        Class<?> first = loader.loadClass(AbstractClassLoaderTest.class.getName());
        Class<?> second = loader.loadClass(AbstractClassLoaderTest.class.getName());
        Class<?> platform = loader.loadClass(String.class.getName());

        assertThat(first).isSameAs(AbstractClassLoaderTest.class);
        assertThat(second).isSameAs(first);
        assertThat(first.getClassLoader()).isSameAs(parent);
        assertThat(platform).isSameAs(String.class);
        assertThat(filteredNames).contains(AbstractClassLoaderTest.class.getName(), String.class.getName());
    }
}
