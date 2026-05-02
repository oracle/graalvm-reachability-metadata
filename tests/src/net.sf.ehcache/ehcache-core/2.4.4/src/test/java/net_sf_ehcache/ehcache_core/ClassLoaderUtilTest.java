/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache_core;

import static org.assertj.core.api.Assertions.assertThat;

import net.sf.ehcache.Element;
import net.sf.ehcache.util.ClassLoaderUtil;
import org.junit.jupiter.api.Test;

public class ClassLoaderUtilTest {
    private static final String ELEMENT_CLASS_NAME = "net.sf.ehcache.Element";

    @Test
    void loadsClassWithContextClassLoader() throws Exception {
        ClassLoader contextClassLoader = new ClassLoader(ClassLoaderUtilTest.class.getClassLoader()) {
        };

        Class<?> loadedClass = withContextClassLoader(contextClassLoader,
                () -> ClassLoaderUtil.loadClass(ELEMENT_CLASS_NAME));

        assertThat(loadedClass).isSameAs(Element.class);
    }

    @Test
    void loadsClassWithFallbackClassLoaderWhenContextClassLoaderCannotResolveIt() throws Exception {
        ClassLoader missingContextClassLoader = new ClassLoader(null) {
        };

        Class<?> loadedClass = withContextClassLoader(missingContextClassLoader,
                () -> ClassLoaderUtil.loadClass(ELEMENT_CLASS_NAME));

        assertThat(loadedClass).isSameAs(Element.class);
    }

    @Test
    void createsNewInstanceWithPublicConstructorArguments() {
        Object instance = ClassLoaderUtil.createNewInstance(ELEMENT_CLASS_NAME,
                new Class[] {Object.class, Object.class, long.class},
                new Object[] {"created-key", "created-value", 42L});

        assertThat(instance).isInstanceOf(Element.class);
        Element element = (Element) instance;
        assertThat(element.getObjectKey()).isEqualTo("created-key");
        assertThat(element.getObjectValue()).isEqualTo("created-value");
        assertThat(element.getVersion()).isEqualTo(42L);
    }

    private static <T> T withContextClassLoader(
            ClassLoader contextClassLoader,
            ThrowingSupplier<T> supplier) throws Exception {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(contextClassLoader);
        try {
            return supplier.get();
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
