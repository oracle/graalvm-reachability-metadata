/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_keycloak.keycloak_client_common_synced;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.Enumeration;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.keycloak.common.util.DelegatingSerializationFilter;

import static org.assertj.core.api.Assertions.assertThat;

public class DelegatingSerializationFilterTest {
    private static final String JAVA_EIGHT_CALLABLE_CLASS_NAME =
            DelegatingSerializationFilterTest.class.getName() + "$JavaEightBranchCallable";
    private static final String DELEGATING_SERIALIZATION_FILTER_CLASS_NAME =
            "org.keycloak.common.util.DelegatingSerializationFilter";

    @Test
    void setsObjectInputFilterOnCurrentJavaRuntime() throws Exception {
        try (ObjectInputStream objectInputStream = newObjectInputStream()) {
            DelegatingSerializationFilter.builder()
                    .addAllowedClass(String.class)
                    .addAllowedPattern("java.lang.*")
                    .setFilter(objectInputStream);

            assertThat(objectInputStream.getObjectInputFilter()).isNotNull();
        }
    }

    @Test
    void createsJavaEightAdapterWhenRunningWithJavaEightFilterApi() throws Exception {
        IsolatedJavaEightClassLoader classLoader = new IsolatedJavaEightClassLoader(
                DelegatingSerializationFilterTest.class.getClassLoader());

        try {
            Callable<?> callable = ServiceLoader.load(Callable.class, classLoader).stream()
                    .map(ServiceLoader.Provider::get)
                    .filter(provider -> provider.getClass().getName().equals(JAVA_EIGHT_CALLABLE_CLASS_NAME))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Expected isolated Java 8 serialization filter callable"));

            assertThat(callable.call()).isEqualTo(Boolean.TRUE);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    public static ObjectInputStream newObjectInputStream() throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)) {
            objectOutputStream.flush();
        }
        return new ObjectInputStream(new ByteArrayInputStream(byteArrayOutputStream.toByteArray()));
    }

    public static final class JavaEightBranchCallable implements Callable<Boolean> {
        @Override
        public Boolean call() throws Exception {
            Thread thread = Thread.currentThread();
            ClassLoader originalClassLoader = thread.getContextClassLoader();
            String originalJavaSpecificationVersion = System.getProperty("java.specification.version");

            try (ObjectInputStream objectInputStream = newObjectInputStream()) {
                thread.setContextClassLoader(JavaEightBranchCallable.class.getClassLoader());
                System.setProperty("java.specification.version", "1.8");

                DelegatingSerializationFilter.builder()
                        .addAllowedClass(String.class)
                        .setFilter(objectInputStream);

                return objectInputStream.getObjectInputFilter() != null;
            } finally {
                thread.setContextClassLoader(originalClassLoader);
                restoreJavaSpecificationVersion(originalJavaSpecificationVersion);
            }
        }

        private static void restoreJavaSpecificationVersion(String originalJavaSpecificationVersion) {
            if (originalJavaSpecificationVersion == null) {
                System.clearProperty("java.specification.version");
                return;
            }

            System.setProperty("java.specification.version", originalJavaSpecificationVersion);
        }
    }

    public static final class JavaEightObjectInputFilterConfig {
        private JavaEightObjectInputFilterConfig() {
        }

        public static Object getObjectInputFilter(ObjectInputStream objectInputStream) {
            return objectInputStream.getObjectInputFilter();
        }

        public static void setObjectInputFilter(
                ObjectInputStream objectInputStream,
                ObjectInputFilter objectInputFilter) {
            objectInputStream.setObjectInputFilter(objectInputFilter);
        }

        public static ObjectInputFilter createFilter(String pattern) {
            return ObjectInputFilter.Config.createFilter(pattern);
        }
    }

    private static final class IsolatedJavaEightClassLoader extends ClassLoader {
        private static final String LEGACY_OBJECT_INPUT_FILTER_CLASS_NAME = "sun.misc.ObjectInputFilter";
        private static final String LEGACY_OBJECT_INPUT_FILTER_CONFIG_CLASS_NAME = "sun.misc.ObjectInputFilter$Config";
        private static final String CALLABLE_SERVICE_RESOURCE_NAME = "META-INF/services/java.util.concurrent.Callable";

        private IsolatedJavaEightClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    loadedClass = loadUnresolvedClass(name);
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        private Class<?> loadUnresolvedClass(String name) throws ClassNotFoundException {
            if (LEGACY_OBJECT_INPUT_FILTER_CLASS_NAME.equals(name)) {
                return ObjectInputFilter.class;
            }
            if (LEGACY_OBJECT_INPUT_FILTER_CONFIG_CLASS_NAME.equals(name)) {
                return JavaEightObjectInputFilterConfig.class;
            }
            if (!isChildFirst(name)) {
                return super.loadClass(name, false);
            }

            try {
                return findClass(name);
            } catch (ClassNotFoundException ignored) {
                return super.loadClass(name, false);
            }
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (CALLABLE_SERVICE_RESOURCE_NAME.equals(name)) {
                return Collections.enumeration(Collections.singleton(serviceProviderUrl()));
            }
            return super.getResources(name);
        }

        private URL serviceProviderUrl() throws IOException {
            return new URL(null, "memory:java-eight-callable", new ServiceProviderUrlStreamHandler());
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            try {
                ClassDefinition classDefinition = readClassDefinition(name);
                byte[] classBytes = classDefinition.bytes();
                return defineClass(name, classBytes, 0, classBytes.length, classDefinition.protectionDomain());
            } catch (IOException exception) {
                throw new ClassNotFoundException(name, exception);
            }
        }

        private ClassDefinition readClassDefinition(String className) throws IOException, ClassNotFoundException {
            String resourceName = className.replace('.', '/') + ".class";
            URL resource = getParent().getResource(resourceName);
            if (resource == null) {
                throw new ClassNotFoundException(className);
            }

            try (InputStream inputStream = resource.openStream()) {
                ProtectionDomain protectionDomain = new ProtectionDomain(
                        new CodeSource(resource, (CodeSigner[]) null),
                        null,
                        this,
                        null);
                return new ClassDefinition(inputStream.readAllBytes(), protectionDomain);
            }
        }

        private boolean isChildFirst(String className) {
            return className.equals(JAVA_EIGHT_CALLABLE_CLASS_NAME)
                    || className.equals(DELEGATING_SERIALIZATION_FILTER_CLASS_NAME)
                    || className.startsWith(DELEGATING_SERIALIZATION_FILTER_CLASS_NAME + "$");
        }
    }

    private record ClassDefinition(byte[] bytes, ProtectionDomain protectionDomain) {
    }

    private static final class ServiceProviderUrlStreamHandler extends URLStreamHandler {
        @Override
        protected URLConnection openConnection(URL url) {
            return new ServiceProviderUrlConnection(url);
        }
    }

    private static final class ServiceProviderUrlConnection extends URLConnection {
        private ServiceProviderUrlConnection(URL url) {
            super(url);
        }

        @Override
        public void connect() {
            // No connection setup is needed for the in-memory service descriptor.
        }

        @Override
        public InputStream getInputStream() {
            String serviceDeclaration = JAVA_EIGHT_CALLABLE_CLASS_NAME + System.lineSeparator();
            return new ByteArrayInputStream(serviceDeclaration.getBytes(StandardCharsets.UTF_8));
        }
    }
}
