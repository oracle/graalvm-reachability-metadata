/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_keycloak.keycloak_client_common_synced;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;
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

public class DelegatingSerializationFilterInnerOnJava6To8Test {
    private static final String DELEGATING_SERIALIZATION_FILTER_CLASS_NAME =
            "org.keycloak.common.util.DelegatingSerializationFilter";
    private static final String KEYCLOAK_UTIL_PACKAGE = "org.keycloak.common.util.";

    private static String lastFilterPattern;
    private static ObjectInputStream lastFilteredObjectInputStream;

    @Test
    void legacyAdapterGetsCreatesAndSetsObjectInputFilterThroughPublicBuilder() throws Exception {
        if (isNativeImageRuntime()) {
            throw new TestAbortedException(
                    "Native image runtime does not support reloading Keycloak classes via isolated URLClassLoader");
        }
        try {
            exerciseLegacyAdapterThroughPublicBuilder();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static void exerciseLegacyAdapterThroughPublicBuilder() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader originalContextClassLoader = thread.getContextClassLoader();
        URL keycloakCodeSourceUrl = codeSourceUrl(DelegatingSerializationFilter.class);
        String originalJavaSpecificationVersion = System.getProperty("java.specification.version");
        lastFilterPattern = null;
        lastFilteredObjectInputStream = null;
        System.setProperty("java.specification.version", "1.8");

        try (ChildFirstClassLoader isolatedKeycloakClassLoader = new ChildFirstClassLoader(new URL[] {
                keycloakCodeSourceUrl
        }, DelegatingSerializationFilterInnerOnJava6To8Test.class.getClassLoader());
                ObjectInputStream objectInputStream = new ObjectInputStream(
                        new ByteArrayInputStream(serializedList()))) {
            thread.setContextClassLoader(new LegacySerializationFilterClassLoader(originalContextClassLoader));

            Class<?> isolatedFilterClass = Class.forName(
                    DELEGATING_SERIALIZATION_FILTER_CLASS_NAME, true, isolatedKeycloakClassLoader);
            Object builder = isolatedFilterClass.getMethod("builder").invoke(null);
            builder.getClass().getMethod("addAllowedClass", Class.class).invoke(builder, ArrayList.class);
            builder.getClass().getMethod("setFilter", ObjectInputStream.class).invoke(builder, objectInputStream);

            assertThat(lastFilteredObjectInputStream).isSameAs(objectInputStream);
            assertThat(lastFilterPattern)
                    .contains(ArrayList.class.getName())
                    .contains("java.util.*")
                    .endsWith(";!*");
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

    private static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
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
            lastFilteredObjectInputStream = objectInputStream;
        }

        public static LegacyObjectInputFilter createFilter(String filterPattern) {
            assertThat(filterPattern).isNotBlank();
            lastFilterPattern = filterPattern;
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
                    loadedClass = loadClassChildFirst(name);
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        private Class<?> loadClassChildFirst(String name) throws ClassNotFoundException {
            if (isChildFirst(name)) {
                try {
                    return findClass(name);
                } catch (ClassNotFoundException exception) {
                    return super.loadClass(name, false);
                }
            }
            return super.loadClass(name, false);
        }

        private boolean isChildFirst(String className) {
            return DELEGATING_SERIALIZATION_FILTER_CLASS_NAME.equals(className)
                    || className.startsWith(KEYCLOAK_UTIL_PACKAGE + "DelegatingSerializationFilter$");
        }
    }
}
