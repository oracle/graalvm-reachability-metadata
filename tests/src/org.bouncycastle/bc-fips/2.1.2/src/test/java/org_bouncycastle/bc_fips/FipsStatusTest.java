/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bc_fips;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.security.AllPermission;
import java.security.CodeSource;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.ProtectionDomain;

import org.bouncycastle.crypto.fips.FipsOperationError;
import org.bouncycastle.crypto.fips.FipsStatus;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@Order(1)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FipsStatusTest {
    private static final String FIPS_STATUS_CLASS_NAME = "org.bouncycastle.crypto.fips.FipsStatus";
    private static final String NATIVE_CPU_VARIANT_PROPERTY = "org.bouncycastle.native.cpu_variant";
    private static final String MARKER_RESOURCE_NAME = "org/bouncycastle/MARKER";

    @BeforeAll
    static void useJavaOnlyNativeVariant() {
        System.setProperty(NATIVE_CPU_VARIANT_PROPERTY, "java");
    }

    @Test
    @Order(1)
    void publicStatusApiInitializesModuleSelfTests() {
        try {
            assertTrue(FipsStatus.isReady());
            assertEquals(FipsStatus.READY, FipsStatus.getStatusMessage());
            assertTrue(FipsStatus.isReady());
        } catch (FipsOperationError error) {
            assertTrue(isChecksumFailure(error), error.getMessage());
        }
    }

    @Test
    @Order(2)
    void moduleHmacLookupReturnsSha256LengthValue() {
        assertEquals(32, FipsStatus.getModuleHMAC().length);
    }

    @Test
    @Order(3)
    void isolatedStatusUsesMarkerResourceWhenCodeSourceIsUnavailable() throws Exception {
        // The isolated loader gives LICENSE a null CodeSource so the public API follows
        // the marker resource fallback used by FipsStatus on restricted runtimes.
        try {
            CodeSource codeSource = FipsStatus.class.getProtectionDomain().getCodeSource();
            MarkerFallbackClassLoader classLoader = new MarkerFallbackClassLoader(
                    FipsStatusTest.class.getClassLoader(), codeSource);
            Class<?> statusClass = classLoader.loadClass(FIPS_STATUS_CLASS_NAME);

            assertEquals(Boolean.TRUE, statusClass.getMethod("isReady").invoke(null));
            assertArrayEquals(new byte[32], (byte[])statusClass.getMethod("getModuleHMAC").invoke(null));
        } catch (ClassNotFoundException exception) {
            assertEquals(32, FipsStatus.getModuleHMAC().length);
        } catch (InvocationTargetException exception) {
            handleInvocationFailure(exception);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static void handleInvocationFailure(InvocationTargetException exception) throws Exception {
        Throwable cause = exception.getCause();
        if (cause instanceof Error error) {
            if (isChecksumFailure(error) || NativeImageSupport.isUnsupportedFeatureError(error)) {
                return;
            }
            throw error;
        }
        if (cause instanceof Exception checkedException) {
            throw checkedException;
        }
        throw exception;
    }

    private static boolean isChecksumFailure(Error error) {
        return "org.bouncycastle.crypto.fips.FipsOperationError".equals(error.getClass().getName())
                && error.getMessage() != null
                && error.getMessage().startsWith("Module checksum failed");
    }

    private static final class MarkerFallbackClassLoader extends ClassLoader {
        private static final String LICENSE_CLASS_NAME = "org.bouncycastle.LICENSE";

        private final ClassLoader resourceLoader;
        private final ProtectionDomain bouncyCastleDomain;
        private final ProtectionDomain markerFallbackDomain;

        MarkerFallbackClassLoader(ClassLoader resourceLoader, CodeSource codeSource) {
            super(null);
            this.resourceLoader = resourceLoader;
            this.bouncyCastleDomain = new ProtectionDomain(codeSource, allPermissions());
            this.markerFallbackDomain = new ProtectionDomain(null, allPermissions());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    loadedClass = loadClassFromBouncyCastleJar(name);
                }
                if (loadedClass == null) {
                    loadedClass = ClassLoader.getPlatformClassLoader().loadClass(name);
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        @Override
        public URL getResource(String name) {
            if (MARKER_RESOURCE_NAME.equals(name)) {
                return markerUrl();
            }
            return resourceLoader.getResource(name);
        }

        private Class<?> loadClassFromBouncyCastleJar(String name) throws ClassNotFoundException {
            if (!name.startsWith("org.bouncycastle.")) {
                return null;
            }
            return findClass(name);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            String resourceName = name.replace('.', '/') + ".class";
            try (InputStream input = resourceLoader.getResourceAsStream(resourceName)) {
                if (input == null) {
                    throw new ClassNotFoundException(name);
                }
                byte[] bytecode = input.readAllBytes();
                return defineClass(name, bytecode, 0, bytecode.length, protectionDomain(name));
            } catch (IOException exception) {
                throw new ClassNotFoundException(name, exception);
            }
        }

        private ProtectionDomain protectionDomain(String className) {
            if (LICENSE_CLASS_NAME.equals(className)) {
                return markerFallbackDomain;
            }
            return bouncyCastleDomain;
        }

        private static PermissionCollection allPermissions() {
            Permissions permissions = new Permissions();
            permissions.add(new AllPermission());
            permissions.setReadOnly();
            return permissions;
        }

        private URL markerUrl() {
            try {
                return new URL(null, "vfs:/bc-fips/MARKER", new URLStreamHandler() {
                    @Override
                    protected URLConnection openConnection(URL url) throws IOException {
                        throw new IOException("marker URL is only used for its external form");
                    }
                });
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
