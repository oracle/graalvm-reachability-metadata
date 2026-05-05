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
import java.util.Dictionary;
import java.util.Enumeration;

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
