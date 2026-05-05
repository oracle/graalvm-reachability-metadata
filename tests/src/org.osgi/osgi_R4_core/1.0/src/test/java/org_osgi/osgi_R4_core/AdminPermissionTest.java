/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.osgi_R4_core;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Base64;
import java.util.Dictionary;
import java.util.Enumeration;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.osgi.framework.AdminPermission;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;

import static org.assertj.core.api.Assertions.assertThat;

public class AdminPermissionTest {
    @Test
    void createsAdminPermissionsThroughConfiguredVendorDelegate() {
        System.setProperty("org.osgi.vendor.framework", "org_osgi.osgi_R4_core.vendor");

        AdminPermission filteredPermission = new AdminPermission(
                "(id=7)",
                "execute,metadata");
        AdminPermission bundlePermission = new AdminPermission(
                new TestBundle(7L),
                AdminPermission.EXECUTE);

        assertThat(filteredPermission.getName()).isEqualTo("(id=7)");
        assertThat(filteredPermission.getActions()).isEqualTo("execute,metadata");
        assertThat(bundlePermission.getName()).isEqualTo("(id=7)");
        assertThat(bundlePermission.getActions()).isEqualTo(AdminPermission.EXECUTE);
        assertThat(filteredPermission.implies(bundlePermission)).isTrue();
    }

    @Test
    void isolatedClassLoadingRunsLegacyClassLiteralHelper() throws Exception {
        System.setProperty("org.osgi.vendor.framework", "org_osgi.osgi_R4_core.vendor");

        try {
            FreshAdminPermissionClassLoader classLoader = new FreshAdminPermissionClassLoader();
            classLoader.verifyRuntimeClassDefinitionSupported();
            Class<?> adminPermissionClass = Class.forName(AdminPermission.class.getName(), true, classLoader);

            assertThat(adminPermissionClass.getName()).isEqualTo(AdminPermission.class.getName());
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static final class FreshAdminPermissionClassLoader extends ClassLoader {
        private FreshAdminPermissionClassLoader() {
            super(AdminPermissionTest.class.getClassLoader());
        }

        private void verifyRuntimeClassDefinitionSupported() {
            byte[] classBytes = Base64.getDecoder().decode(
                    "yv66vgAAADQACgoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ"
                            + "+AQADKClWBwAIAQAwb3JnX29zZ2kvb3NnaV9SNF9jb3JlL0FkbWluUGVybWlzc2lvbkRlZmluZVByb2JlAQAEQ29kZQAhAAcAAgAAAAAAAQABAAUABgABAAkAAAARAAEAAQAAAAUqtwABsQAAAAAAAA==");
            defineClass(classBytes, 0, classBytes.length);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    loadedClass = isIsolatedClass(name) ? findClass(name) : super.loadClass(name, false);
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            String resourceName = name.replace('.', '/') + ".class";
            try (InputStream input = getParent().getResourceAsStream(resourceName)) {
                if (input == null) {
                    throw new ClassNotFoundException(name);
                }
                byte[] classBytes = input.readAllBytes();
                return defineClass(name, classBytes, 0, classBytes.length);
            } catch (IOException exception) {
                throw new ClassNotFoundException(name, exception);
            }
        }

        private static boolean isIsolatedClass(String name) {
            return AdminPermission.class.getName().equals(name)
                    || "org.osgi.framework.AdminPermission$1".equals(name)
                    || org_osgi.osgi_R4_core.vendor.AdminPermission.class.getName().equals(name);
        }
    }

    private static final class TestBundle implements Bundle {
        private final long bundleId;

        private TestBundle(long bundleId) {
            this.bundleId = bundleId;
        }

        @Override
        public int getState() {
            return ACTIVE;
        }

        @Override
        public void start() throws BundleException {
        }

        @Override
        public void stop() throws BundleException {
        }

        @Override
        public void update() throws BundleException {
        }

        @Override
        public void update(InputStream input) throws BundleException {
        }

        @Override
        public void uninstall() throws BundleException {
        }

        @Override
        public Dictionary getHeaders() {
            return null;
        }

        @Override
        public long getBundleId() {
            return bundleId;
        }

        @Override
        public String getLocation() {
            return "memory:test-bundle";
        }

        @Override
        public ServiceReference[] getRegisteredServices() {
            return null;
        }

        @Override
        public ServiceReference[] getServicesInUse() {
            return null;
        }

        @Override
        public boolean hasPermission(Object permission) {
            return true;
        }

        @Override
        public URL getResource(String name) {
            return null;
        }

        @Override
        public Dictionary getHeaders(String locale) {
            return getHeaders();
        }

        @Override
        public String getSymbolicName() {
            return "org.example.testbundle";
        }

        @Override
        public Class loadClass(String name) throws ClassNotFoundException {
            throw new ClassNotFoundException(name);
        }

        @Override
        public Enumeration getResources(String name) throws IOException {
            return null;
        }

        @Override
        public Enumeration getEntryPaths(String path) {
            return null;
        }

        @Override
        public URL getEntry(String path) {
            return null;
        }

        @Override
        public long getLastModified() {
            return 0L;
        }

        @Override
        public Enumeration findEntries(String path, String filePattern, boolean recurse) {
            return null;
        }
    }
}
