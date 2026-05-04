/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_util;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;

import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectHelperTest {
    private static final String RESOURCE_NAME = "object-helper-resource.txt";

    @Test
    void loadsNonSimpleArrayClassByName() {
        Class<?> arrayClass = ObjectHelper.loadClass(ObjectHelperArrayElement.class.getName() + "[]");

        assertThat(arrayClass).isEqualTo(ObjectHelperArrayElement[].class);
    }

    @Test
    void detectsDefaultConstructors() {
        assertThat(ObjectHelper.hasDefaultPublicNoArgConstructor(PublicDefaultConstructor.class)).isTrue();
        assertThat(ObjectHelper.hasDefaultNoArgConstructor(ProtectedDefaultConstructor.class)).isTrue();
        assertThat(ObjectHelper.hasDefaultNoArgConstructor(OnlyPrivateDefaultConstructor.class)).isFalse();
    }

    @Test
    void looksUpPublicConstantFieldValues() {
        assertThat(ObjectHelper.lookupConstantFieldValue(Constants.class, ".ANSWER")).isEqualTo("forty-two");
        assertThat(ObjectHelper.lookupConstantFieldValue(Constants.class, "MISSING")).isNull();
        assertThat(ObjectHelper.lookupConstantFieldValue(null, "ANSWER")).isNull();
    }

    @Test
    void loadsResourceUrlFromProvidedClassLoader() {
        URL url = ObjectHelper.loadResourceAsURL(RESOURCE_NAME, currentClassLoader());

        assertThat(url).isNotNull();
    }

    @Test
    void loadsResourceUrlFromThreadContextClassLoader() {
        URL url = ObjectHelper.loadResourceAsURL(RESOURCE_NAME);

        assertThat(url).isNotNull();
    }

    @Test
    void loadsResourceUrlFromClassLoadedThroughThreadContextClassLoader() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(new CamelContextLikeClassLoader());
        try {
            URL url = ObjectHelper.loadResourceAsURL(RESOURCE_NAME);

            assertThat(url).isNotNull();
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void loadsResourceUrlFromObjectHelperClassLoaderFallback() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(null);
        try {
            URL url = ObjectHelper.loadResourceAsURL(RESOURCE_NAME);

            assertThat(url).isNotNull();
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void loadsAbsoluteResourceUrlFromObjectHelperClassFallback() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(null);
        try {
            URL url = ObjectHelper.loadResourceAsURL("/" + RESOURCE_NAME);

            assertThat(url).isNotNull();
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void loadsResourcesFromProvidedClassLoader() {
        Enumeration<URL> urls = ObjectHelper.loadResourcesAsURL(RESOURCE_NAME, currentClassLoader());

        assertThat(urls).isNotNull();
    }

    @Test
    void loadsResourcesFromThreadContextClassLoader() {
        Enumeration<URL> urls = ObjectHelper.loadResourcesAsURL(RESOURCE_NAME);

        assertThat(urls).isNotNull();
    }

    @Test
    void loadsResourcesFromClassLoadedThroughThreadContextClassLoader() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(new CamelContextLikeClassLoader());
        try {
            Enumeration<URL> urls = ObjectHelper.loadResourcesAsURL(RESOURCE_NAME);

            assertThat(urls).isNotNull();
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void loadsResourcesFromObjectHelperClassLoaderFallback() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(null);
        try {
            Enumeration<URL> urls = ObjectHelper.loadResourcesAsURL(RESOURCE_NAME);

            assertThat(urls).isNotNull();
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private static ClassLoader currentClassLoader() {
        return ObjectHelperTest.class.getClassLoader();
    }

    public static final class PublicDefaultConstructor {
        public PublicDefaultConstructor() {
        }
    }

    static final class ProtectedDefaultConstructor {
        protected ProtectedDefaultConstructor() {
        }
    }

    static final class OnlyPrivateDefaultConstructor {
        private OnlyPrivateDefaultConstructor() {
        }
    }

    public static final class Constants {
        public static final String ANSWER = "forty-two";

        private Constants() {
        }
    }

    private static final class CamelContextLikeClassLoader extends ClassLoader {
        CamelContextLikeClassLoader() {
            super(currentClassLoader());
        }

        @Override
        public URL getResource(String name) {
            return null;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            return null;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if ("org.apache.camel.impl.DefaultCamelContext".equals(name)) {
                return ObjectHelper.class;
            }
            return super.loadClass(name);
        }
    }
}

final class ObjectHelperArrayElement {
}
