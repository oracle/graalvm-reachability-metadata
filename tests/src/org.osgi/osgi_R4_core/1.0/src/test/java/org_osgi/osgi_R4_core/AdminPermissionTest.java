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
import java.net.URL;
import java.util.Dictionary;
import java.util.Enumeration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;

public class AdminPermissionTest {
    private static final String IMPLEMENTATION_PROPERTY = "org.osgi.vendor.framework";
    private static final String IMPLEMENTATION_PACKAGE =
            "org_osgi.osgi_R4_core.vendor.framework";

    private static String previousImplementationPackage;

    @BeforeAll
    static void setUpVendorImplementation() {
        previousImplementationPackage = System.getProperty(IMPLEMENTATION_PROPERTY);
        System.setProperty(IMPLEMENTATION_PROPERTY, IMPLEMENTATION_PACKAGE);
    }

    @AfterAll
    static void restoreVendorImplementation() {
        if (previousImplementationPackage == null) {
            System.clearProperty(IMPLEMENTATION_PROPERTY);
        } else {
            System.setProperty(IMPLEMENTATION_PROPERTY, previousImplementationPackage);
        }
    }

    @BeforeEach
    void resetVendorState() {
        org_osgi.osgi_R4_core.vendor.framework.AdminPermission.reset();
    }

    @Test
    void filterConstructorLoadsAndInstantiatesConfiguredVendorPermission() {
        org.osgi.framework.AdminPermission permission =
                new org.osgi.framework.AdminPermission("(id>=1)", "class,metadata");

        assertThat(permission.getName()).isEqualTo("(id>=1)");
        assertThat(permission.getActions()).isEqualTo("class,metadata");
        assertThat(org_osgi.osgi_R4_core.vendor.framework.AdminPermission.stringConstructorCalls)
                .isEqualTo(1);
        assertThat(org_osgi.osgi_R4_core.vendor.framework.AdminPermission.lastCreatedName)
                .isEqualTo("(id>=1)");
        assertThat(org_osgi.osgi_R4_core.vendor.framework.AdminPermission.lastCreatedActions)
                .isEqualTo("class,metadata");
    }

    @Test
    void bundleConstructorInstantiatesConfiguredVendorPermissionWithBundleContext() {
        Bundle bundle = new TestBundle(42L, "example.bundle", "file:/example-bundle.jar");

        org.osgi.framework.AdminPermission permission =
                new org.osgi.framework.AdminPermission(bundle, "execute,resource");

        assertThat(permission.getName()).isEqualTo("(id=42)");
        assertThat(permission.getActions()).isEqualTo("execute,resource");
        assertThat(org_osgi.osgi_R4_core.vendor.framework.AdminPermission.bundleConstructorCalls)
                .isEqualTo(1);
        assertThat(org_osgi.osgi_R4_core.vendor.framework.AdminPermission.lastCreatedBundleId)
                .isEqualTo(42L);
        assertThat(org_osgi.osgi_R4_core.vendor.framework.AdminPermission.lastCreatedActions)
                .isEqualTo("execute,resource");
    }

    private static final class TestBundle implements Bundle {
        private final long bundleId;
        private final String symbolicName;
        private final String location;

        private TestBundle(long bundleId, String symbolicName, String location) {
            this.bundleId = bundleId;
            this.symbolicName = symbolicName;
            this.location = location;
        }

        @Override
        public int getState() {
            return Bundle.ACTIVE;
        }

        @Override
        public void start() throws BundleException {
            throw new UnsupportedOperationException("Lifecycle operations are not used by this test");
        }

        @Override
        public void stop() throws BundleException {
            throw new UnsupportedOperationException("Lifecycle operations are not used by this test");
        }

        @Override
        public void update() throws BundleException {
            throw new UnsupportedOperationException("Lifecycle operations are not used by this test");
        }

        @Override
        public void update(InputStream in) throws BundleException {
            throw new UnsupportedOperationException("Lifecycle operations are not used by this test");
        }

        @Override
        public void uninstall() throws BundleException {
            throw new UnsupportedOperationException("Lifecycle operations are not used by this test");
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
            return location;
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
            return null;
        }

        @Override
        public String getSymbolicName() {
            return symbolicName;
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
        public URL getEntry(String name) {
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
