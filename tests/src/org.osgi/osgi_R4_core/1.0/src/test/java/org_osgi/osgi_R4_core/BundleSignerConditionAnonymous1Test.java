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
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.condpermadmin.BundleSignerCondition;
import org.osgi.service.condpermadmin.Condition;
import org.osgi.service.condpermadmin.ConditionInfo;

import static org.assertj.core.api.Assertions.assertThat;

public class BundleSignerConditionAnonymous1Test {
    private static final String CONDITION_TYPE = "org.osgi.service.condpermadmin.BundleSignerCondition";

    @Test
    void getConditionLoadsConfiguredVendorDelegate() {
        System.setProperty("org.osgi.vendor.condpermadmin", "org_osgi.osgi_R4_core.vendor");
        ConditionInfo info = new ConditionInfo(CONDITION_TYPE, new String[] {"CN=Example Signer"});

        Condition condition = BundleSignerCondition.getCondition(new TestBundle(), info);

        assertThat(condition.isPostponed()).isFalse();
        assertThat(condition.isMutable()).isFalse();
        assertThat(condition.isSatisfied()).isTrue();
        assertThat(condition.isSatisfied(new Condition[] {condition}, new Properties())).isTrue();
    }

    private static final class TestBundle implements Bundle {
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
            return new Properties();
        }

        @Override
        public long getBundleId() {
            return 42L;
        }

        @Override
        public String getLocation() {
            return "memory:signed-bundle";
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
            return "org.example.signed";
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
