/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu_inject.cglib;

import static org.assertj.core.api.Assertions.assertThat;

import net.sf.cglib.transform.AbstractClassTransformer;
import net.sf.cglib.transform.ClassFilter;
import net.sf.cglib.transform.ClassTransformer;
import net.sf.cglib.transform.ClassTransformerFactory;
import net.sf.cglib.transform.TransformingClassLoader;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class AbstractClassLoaderTest {

    @Test
    void delegatesClassesRejectedByTheFilter() throws ClassNotFoundException {
        TransformingClassLoader loader = new TransformingClassLoader(
                AbstractClassLoaderTest.class.getClassLoader(),
                new ClassFilterOnly(),
                new IdentityClassTransformerFactory());

        Class<?> loadedClass = loader.loadClass(String.class.getName());

        assertThat(loadedClass).isSameAs(String.class);
    }

    @Test
    void transformsClassBytesLoadedFromTheConfiguredClassPath() throws ClassNotFoundException {
        TransformingClassLoader loader = new TransformingClassLoader(
                AbstractClassLoaderTest.class.getClassLoader(),
                new ClassFilterOnly(),
                new IdentityClassTransformerFactory());

        try {
            Class<?> loadedClass = loader.loadClass(ClassFilter.class.getName());

            assertThat(loadedClass).isNotSameAs(ClassFilter.class);
            assertThat(loadedClass.getClassLoader()).isSameAs(loader);
            assertThat(loadedClass.isInterface()).isTrue();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static final class ClassFilterOnly implements ClassFilter {
        @Override
        public boolean accept(String className) {
            return ClassFilter.class.getName().equals(className);
        }
    }

    private static final class IdentityClassTransformerFactory implements ClassTransformerFactory {
        @Override
        public ClassTransformer newInstance() {
            return new IdentityClassTransformer();
        }
    }

    private static final class IdentityClassTransformer extends AbstractClassTransformer {
    }
}
