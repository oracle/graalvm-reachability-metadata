/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.cert.X509Certificate;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.osgi.framework.AdaptPermission;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;

public class AdaptPermissionCollection1Test {
    @Test
    void loadsAdaptPermissionCollectionClass() throws ClassNotFoundException {
        assertThat(Class.forName("org.osgi.framework.AdaptPermissionCollection").getName())
                .isEqualTo("org.osgi.framework.AdaptPermissionCollection");
    }

    @Test
    void newPermissionCollectionStartsEmptyAndAppliesWildcardPermission() {
        AdaptPermission permission = new AdaptPermission("*", AdaptPermission.ADAPT);
        PermissionCollection collection = permission.newPermissionCollection();

        assertThat(collection.getClass().getName())
                .isEqualTo("org.osgi.framework.AdaptPermissionCollection");
        assertThat(collection.elements().hasMoreElements()).isFalse();

        collection.add(permission);

        assertThat(
                        collection.implies(
                                new AdaptPermission(
                                        "com.example.Type",
                                        new TestBundle(),
                                        AdaptPermission.ADAPT)))
                .isTrue();
    }

    @Test
    void serializedEmptyPermissionCollectionRemainsEmpty()
            throws IOException, ClassNotFoundException {
        PermissionCollection collection =
                new AdaptPermission("*", AdaptPermission.ADAPT).newPermissionCollection();

        PermissionCollection restoredCollection = roundTrip(collection);

        assertThat(restoredCollection.getClass().getName())
                .isEqualTo("org.osgi.framework.AdaptPermissionCollection");
        assertThat(restoredCollection.elements().hasMoreElements()).isFalse();
    }

    @Test
    void serializedPermissionCollectionStoresWildcardPermission()
            throws IOException, ClassNotFoundException {
        AdaptPermission permission = new AdaptPermission("*", AdaptPermission.ADAPT);
        PermissionCollection collection = permission.newPermissionCollection();

        collection.add(permission);

        PermissionCollection restoredCollection = roundTrip(collection);
        Enumeration<Permission> elements = restoredCollection.elements();

        assertThat(
                        restoredCollection.implies(
                                new AdaptPermission(
                                        "com.example.Type",
                                        new TestBundle(),
                                        AdaptPermission.ADAPT)))
                .isTrue();
        assertThat(elements.hasMoreElements()).isTrue();
        assertThat(elements.nextElement()).isEqualTo(permission);
    }

    private PermissionCollection roundTrip(PermissionCollection collection)
            throws IOException, ClassNotFoundException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(collection);
        }

        try (ObjectInputStream objectInput =
                new ObjectInputStream(new ByteArrayInputStream(output.toByteArray()))) {
            return (PermissionCollection) objectInput.readObject();
        }
    }

    private static final class TestBundle implements Bundle {
        @Override
        public int getState() {
            return Bundle.ACTIVE;
        }

        @Override
        public void start(int options) throws BundleException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void start() throws BundleException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void stop(int options) throws BundleException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void stop() throws BundleException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void update(InputStream input) throws BundleException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void update() throws BundleException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void uninstall() throws BundleException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Dictionary<String, String> getHeaders() {
            return null;
        }

        @Override
        public long getBundleId() {
            return 1L;
        }

        @Override
        public String getLocation() {
            return "test-location";
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
        public Dictionary<String, String> getHeaders(String locale) {
            return null;
        }

        @Override
        public String getSymbolicName() {
            return "test.bundle";
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            throw new ClassNotFoundException(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            return null;
        }

        @Override
        public Enumeration<String> getEntryPaths(String path) {
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
        public Enumeration<URL> findEntries(String path, String filePattern, boolean recurse) {
            return null;
        }

        @Override
        public BundleContext getBundleContext() {
            return null;
        }

        @Override
        public Map<X509Certificate, List<X509Certificate>> getSignerCertificates(int signersType) {
            return Map.of();
        }

        @Override
        public Version getVersion() {
            return null;
        }

        @Override
        public Object adapt(Class type) {
            return null;
        }

        @Override
        public File getDataFile(String filename) {
            return null;
        }

        @Override
        public int compareTo(Object other) {
            return Long.compare(getBundleId(), ((Bundle) other).getBundleId());
        }
    }
}
