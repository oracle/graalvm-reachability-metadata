/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_common;

import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;

import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PlatformDependent0$6Test {
    private static final String IMAGE_CODE_PROPERTY = "org.graalvm.nativeimage.imagecode";
    private static final String TRY_REFLECTION_SET_ACCESSIBLE_PROPERTY = "io.netty.tryReflectionSetAccessible";
    private static final String NETTY_NO_UNSAFE_PROPERTY = "io.netty.noUnsafe";
    private static final String NETTY_TRY_UNSAFE_PROPERTY = "io.netty.tryUnsafe";
    private static final String LEGACY_NETTY_TRY_UNSAFE_PROPERTY = "org.jboss.netty.tryUnsafe";

    @Test
    void initializesUnalignedDetectionThroughTheBitsMethodFallback() throws Exception {
        URL isolatedLibraryLocation = findIsolatedLibraryLocation();
        Assertions.assertNotNull(isolatedLibraryLocation, "Expected netty-common to have a code source");

        boolean expected = PlatformDependent.isUnaligned();

        try (SystemPropertiesRestorer ignored = new SystemPropertiesRestorer(
                IMAGE_CODE_PROPERTY,
                TRY_REFLECTION_SET_ACCESSIBLE_PROPERTY,
                NETTY_NO_UNSAFE_PROPERTY,
                NETTY_TRY_UNSAFE_PROPERTY,
                LEGACY_NETTY_TRY_UNSAFE_PROPERTY
        )) {
            System.setProperty(IMAGE_CODE_PROPERTY, "runtime");
            System.setProperty(TRY_REFLECTION_SET_ACCESSIBLE_PROPERTY, "true");
            System.clearProperty(NETTY_NO_UNSAFE_PROPERTY);
            System.clearProperty(NETTY_TRY_UNSAFE_PROPERTY);
            System.clearProperty(LEGACY_NETTY_TRY_UNSAFE_PROPERTY);

            try (NettyIsolatedClassLoader classLoader = new NettyIsolatedClassLoader(isolatedLibraryLocation)) {
                Class<?> platformDependentClass =
                        Class.forName("io.netty.util.internal.PlatformDependent", true, classLoader);
                Method isUnalignedMethod = platformDependentClass.getMethod("isUnaligned");

                boolean actual = (Boolean) isUnalignedMethod.invoke(null);

                Assertions.assertEquals(expected, actual);
            }
        }
    }

    private static URL findIsolatedLibraryLocation() throws MalformedURLException, URISyntaxException {
        CodeSource codeSource = PlatformDependent.class.getProtectionDomain().getCodeSource();
        if (codeSource != null) {
            URL location = codeSource.getLocation();
            if (location != null) {
                String externalForm = location.toExternalForm();
                if (externalForm.endsWith(".jar") || externalForm.endsWith("/")) {
                    return location;
                }
            }
        }

        String resourceName = "io/netty/util/internal/PlatformDependent.class";
        URL resource = PlatformDependent0$6Test.class.getClassLoader().getResource(resourceName);
        if (resource == null) {
            return null;
        }

        if ("jar".equals(resource.getProtocol())) {
            String externalForm = resource.toExternalForm();
            int separatorIndex = externalForm.indexOf("!/");
            return URI.create(externalForm.substring("jar:".length(), separatorIndex)).toURL();
        }

        if ("file".equals(resource.getProtocol())) {
            String externalForm = resource.toExternalForm();
            return URI.create(externalForm.substring(0, externalForm.length() - resourceName.length())).toURL();
        }

        return null;
    }

    private static final class NettyIsolatedClassLoader extends URLClassLoader {
        private NettyIsolatedClassLoader(URL libraryLocation) {
            super(new URL[] {libraryLocation}, PlatformDependent0$6Test.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null && name.startsWith("io.netty.")) {
                    try {
                        loadedClass = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        loadedClass = null;
                    }
                }
                if (loadedClass == null) {
                    loadedClass = super.loadClass(name, false);
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }
    }

    private static final class SystemPropertiesRestorer implements AutoCloseable {
        private final String[] keys;
        private final String[] values;

        private SystemPropertiesRestorer(String... keys) {
            this.keys = keys;
            this.values = new String[keys.length];
            for (int i = 0; i < keys.length; i++) {
                values[i] = System.getProperty(keys[i]);
            }
        }

        @Override
        public void close() {
            for (int i = 0; i < keys.length; i++) {
                if (values[i] == null) {
                    System.clearProperty(keys[i]);
                } else {
                    System.setProperty(keys[i], values[i]);
                }
            }
        }
    }
}
