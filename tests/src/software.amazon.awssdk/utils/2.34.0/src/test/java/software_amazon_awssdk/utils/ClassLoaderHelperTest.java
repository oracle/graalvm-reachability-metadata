/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.utils.ClassLoaderHelper;

public class ClassLoaderHelperTest {
    @Test
    void loadClassUsesProvidedClassLoaderBeforeContextLoader() throws Exception {
        Class<?> loadedClass = ClassLoaderHelper.loadClass(String.class.getName(), true, ClassLoaderHelperTest.class);

        assertThat(loadedClass).isSameAs(String.class);
    }

    @Test
    void loadClassUsesThreadContextClassLoaderBeforeProvidedClassLoaders() throws Exception {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(ClassLoaderHelperTest.class.getClassLoader());
        try {
            Class<?> loadedClass = ClassLoaderHelper.loadClass(Integer.class.getName(), false, (Class<?>[]) null);

            assertThat(loadedClass).isSameAs(Integer.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    void loadClassThrowsClassNotFoundAfterTryingAllLoaders() {
        String missingClassName = "example.missing.ClassLoaderHelperTarget";

        assertThatThrownBy(() -> ClassLoaderHelper.loadClass(missingClassName, true, (Class<?>[]) null))
                .isInstanceOf(ClassNotFoundException.class)
                .hasMessage(missingClassName);
    }
}
