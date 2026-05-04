/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.osgi_R4_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import org.junit.jupiter.api.Test;
import org.osgi.framework.AdminPermission;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;

public class AdminPermissionTest {
    static {
        System.setProperty("org.osgi.vendor.framework", "org_osgi.osgi_R4_core.vendor");
    }

    @Test
    void stringFilterConstructorCreatesVendorDelegate() {
        AdminPermission permission = new AdminPermission("(id>=1)", "execute");

        assertThat(permission.getName()).isEqualTo("(id>=1)");
        assertThat(permission.getActions()).isEqualTo("execute");
    }

    @Test
    void bundleConstructorCreatesVendorDelegate() {
        AdminPermission permission = new AdminPermission(new TestBundle(7L), "class,metadata");

        assertThat(permission.getName()).isEqualTo("(id=7)");
        assertThat(permission.getActions()).isEqualTo("class,metadata");
    }

    @Test
    void classLiteralHelperLoadsRequestedClass() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(AdminPermission.class, MethodHandles.lookup());
        MethodHandle classLiteralHelper = lookup.findStatic(
                AdminPermission.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        Class<?> loadedClass = (Class<?>) classLiteralHelper.invoke("java.lang.String");

        assertThat(loadedClass).isEqualTo(String.class);
    }

    private static final class TestBundle implements Bundle {
        private final long bundleId;

        private TestBundle(long bundleId) {
            this.bundleId = bundleId;
        }

        public int getState() {
            return ACTIVE;
        }

        public void start() throws BundleException {
            throw new BundleException("Lifecycle operations are not supported by the test bundle");
        }

        public void stop() throws BundleException {
            throw new BundleException("Lifecycle operations are not supported by the test bundle");
        }

        public void update() throws BundleException {
            throw new BundleException("Lifecycle operations are not supported by the test bundle");
        }

        public void update(InputStream in) throws BundleException {
            throw new BundleException("Lifecycle operations are not supported by the test bundle");
        }

        public void uninstall() throws BundleException {
            throw new BundleException("Lifecycle operations are not supported by the test bundle");
        }

        public Dictionary getHeaders() {
            return null;
        }

        public long getBundleId() {
            return bundleId;
        }

        public String getLocation() {
            return "test-bundle";
        }

        public ServiceReference[] getRegisteredServices() {
            return new ServiceReference[0];
        }

        public ServiceReference[] getServicesInUse() {
            return new ServiceReference[0];
        }

        public boolean hasPermission(Object permission) {
            return true;
        }

        public URL getResource(String name) {
            return null;
        }

        public Dictionary getHeaders(String locale) {
            return getHeaders();
        }

        public String getSymbolicName() {
            return "org.example.test.bundle";
        }

        public Class loadClass(String name) throws ClassNotFoundException {
            throw new ClassNotFoundException(name);
        }

        public Enumeration getResources(String name) throws IOException {
            return Collections.emptyEnumeration();
        }

        public Enumeration getEntryPaths(String path) {
            return Collections.emptyEnumeration();
        }

        public URL getEntry(String name) {
            return null;
        }

        public long getLastModified() {
            return 0L;
        }

        public Enumeration findEntries(String path, String filePattern, boolean recurse) {
            return Collections.emptyEnumeration();
        }
    }
}
