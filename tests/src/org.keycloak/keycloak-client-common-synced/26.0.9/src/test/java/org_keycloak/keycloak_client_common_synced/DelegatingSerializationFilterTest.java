/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_keycloak.keycloak_client_common_synced;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.keycloak.common.util.DelegatingSerializationFilter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class DelegatingSerializationFilterTest {
    private static final String DELEGATING_SERIALIZATION_FILTER_CLASS_NAME =
            "org.keycloak.common.util.DelegatingSerializationFilter";
    private static final String KEYCLOAK_UTIL_PACKAGE = "org.keycloak.common.util.";

    @Test
    void setsJavaAfter8ObjectInputFilterThroughPublicBuilder() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader originalContextClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(DelegatingSerializationFilterTest.class.getClassLoader());

        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serializedList()))) {
            DelegatingSerializationFilter.builder()
                    .addAllowedClass(ArrayList.class)
                    .setFilter(objectInputStream);

            assertThat(objectInputStream.getObjectInputFilter()).isNotNull();
        } finally {
            thread.setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    void initializesLegacyJavaSerializationFilterAdapterWithContextClassLoader() throws Exception {
        try {
            initializeLegacyJavaSerializationFilterAdapterWithContextClassLoader();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static void initializeLegacyJavaSerializationFilterAdapterWithContextClassLoader() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader originalContextClassLoader = thread.getContextClassLoader();
        URL keycloakCodeSourceUrl = codeSourceUrl(DelegatingSerializationFilter.class);
        String originalJavaSpecificationVersion = System.getProperty("java.specification.version");
        System.setProperty("java.specification.version", "1.8");

        try (ChildFirstClassLoader isolatedKeycloakClassLoader = new ChildFirstClassLoader(new URL[] {
                keycloakCodeSourceUrl
        }, DelegatingSerializationFilterTest.class.getClassLoader())) {
            thread.setContextClassLoader(new LegacySerializationFilterClassLoader(originalContextClassLoader));

            Class<?> isolatedFilterClass = Class.forName(
                    DELEGATING_SERIALIZATION_FILTER_CLASS_NAME, true, isolatedKeycloakClassLoader);

            assertThat(isolatedFilterClass.getClassLoader()).isSameAs(isolatedKeycloakClassLoader);
        } finally {
            restoreJavaSpecificationVersion(originalJavaSpecificationVersion);
            thread.setContextClassLoader(originalContextClassLoader);
        }
    }

    private static byte[] serializedList() throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)) {
            objectOutputStream.writeObject(new ArrayList<>(List.of("keycloak")));
        }
        return byteArrayOutputStream.toByteArray();
    }

    private static URL codeSourceUrl(Class<?> type) {
        CodeSource codeSource = type.getProtectionDomain().getCodeSource();

        assertThat(codeSource).isNotNull();
        return codeSource.getLocation();
    }

    private static void restoreJavaSpecificationVersion(String originalJavaSpecificationVersion) {
        if (originalJavaSpecificationVersion == null) {
            System.clearProperty("java.specification.version");
        } else {
            System.setProperty("java.specification.version", originalJavaSpecificationVersion);
        }
    }

    public static final class LegacyObjectInputFilter {
    }

    public static final class LegacyObjectInputFilterConfig {
        public static Object getObjectInputFilter(ObjectInputStream objectInputStream) {
            assertThat(objectInputStream).isNotNull();
            return null;
        }

        public static void setObjectInputFilter(
                ObjectInputStream objectInputStream, LegacyObjectInputFilter objectInputFilter) {
            assertThat(objectInputStream).isNotNull();
            assertThat(objectInputFilter).isNotNull();
        }

        public static LegacyObjectInputFilter createFilter(String filterPattern) {
            assertThat(filterPattern).isNotBlank();
            return new LegacyObjectInputFilter();
        }
    }

    private static final class LegacySerializationFilterClassLoader extends ClassLoader {
        private LegacySerializationFilterClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if ("sun.misc.ObjectInputFilter".equals(name)) {
                return LegacyObjectInputFilter.class;
            }
            if ("sun.misc.ObjectInputFilter$Config".equals(name)) {
                return LegacyObjectInputFilterConfig.class;
            }
            return super.loadClass(name);
        }
    }

    private static final class ChildFirstClassLoader extends URLClassLoader {
        private ChildFirstClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    if (isChildFirst(name)) {
                        try {
                            loadedClass = findClass(name);
                        } catch (ClassNotFoundException exception) {
                            loadedClass = super.loadClass(name, false);
                        }
                    } else {
                        loadedClass = super.loadClass(name, false);
                    }
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        private boolean isChildFirst(String className) {
            return DELEGATING_SERIALIZATION_FILTER_CLASS_NAME.equals(className)
                    || className.startsWith(KEYCLOAK_UTIL_PACKAGE + "DelegatingSerializationFilter$");
        }
    }
}
