/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.io.resource.ClassPathResource;
import cn.hutool.core.io.resource.NoResourceException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ClassPathResourceTest {
    @Test
    public void looksUpResourceRelativeToProvidedClass() {
        assertThatThrownBy(() -> new ClassPathResource("missing-class-relative-resource.txt", ClassPathResourceTest.class))
                .isInstanceOf(NoResourceException.class)
                .hasMessageContaining("missing-class-relative-resource.txt");
    }

    @Test
    public void looksUpResourceWithProvidedClassLoader() {
        String resourcePath = "cn_hutool/hutool_all/missing-class-loader-resource.txt";
        ClassLoader classLoader = ClassLoader.getPlatformClassLoader();

        assertThatThrownBy(() -> new ClassPathResource(resourcePath, classLoader))
                .isInstanceOf(NoResourceException.class)
                .hasMessageContaining(resourcePath);
    }

    @Test
    public void fallsBackWhenThreadContextClassLoaderIsUnavailable() {
        String resourcePath = "cn_hutool/hutool_all/missing-default-resource.txt";
        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();

        try {
            currentThread.setContextClassLoader(null);

            assertThatThrownBy(() -> new ClassPathResource(resourcePath))
                    .isInstanceOf(NoResourceException.class)
                    .hasMessageContaining(resourcePath);
        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    public void invokesSystemLookupWhenNoClassOrClassLoaderIsAvailable() throws Exception {
        String resourcePath = "cn_hutool/hutool_all/missing-system-resource.txt";
        ClassPathResource resource = new ClassPathResource(resourcePath, new FixedUrlClassLoader());
        setField(resource, "clazz", null);
        setField(resource, "classLoader", null);
        Method initUrl = ClassPathResource.class.getDeclaredMethod("initUrl");
        initUrl.setAccessible(true);

        assertThatThrownBy(() -> invokeInitUrl(resource, initUrl))
                .isInstanceOf(NoResourceException.class)
                .hasMessageContaining(resourcePath);
    }

    private static void setField(ClassPathResource resource, String fieldName, Object value)
            throws ReflectiveOperationException {
        Field field = ClassPathResource.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(resource, value);
    }

    private static void invokeInitUrl(ClassPathResource resource, Method initUrl) throws Throwable {
        try {
            initUrl.invoke(resource);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    private static class FixedUrlClassLoader extends ClassLoader {
        private final URL resourceUrl;

        FixedUrlClassLoader() throws MalformedURLException {
            this.resourceUrl = new URL("file:/class-path-resource-fixture");
        }

        @Override
        public URL getResource(String name) {
            return resourceUrl;
        }
    }
}
