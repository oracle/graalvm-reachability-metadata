/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URL;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.CapabilityPermission;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;

public class CapabilityPermissionCollection1Test {
    @Test
    void permissionCollectionSerializesAndMatchesDirectWildcardAndFilteredPermissions() throws Exception {
        TestBundle bundle = new TestBundle(7L, "com.example.bundle", "file:/bundle");
        CapabilityPermission filteredRequirePermission = new CapabilityPermission(
                "(&(capability.namespace=osgi.service)(vendor=acme)(name=com.example.bundle)(id=7)(location=file:/bundle))",
                "require");
        PermissionCollection permissionCollection = filteredRequirePermission.newPermissionCollection();
        permissionCollection.add(filteredRequirePermission);
        permissionCollection.add(new CapabilityPermission("*", "provide"));
        permissionCollection.add(new CapabilityPermission("*", "require"));

        PermissionCollection restoredPermissionCollection = serializeAndDeserialize(permissionCollection);
        CapabilityPermission requestedRequirePermission = new CapabilityPermission(
                "osgi.service",
                Map.of("vendor", "acme", "mode", "sync"),
                bundle,
                "require");
        CapabilityPermission mismatchedRequirePermission = new CapabilityPermission(
                "osgi.service",
                Map.of("vendor", "other"),
                bundle,
                "require");
        List<String> grantedPermissions = Collections.list(restoredPermissionCollection.elements()).stream()
                .map(Permission.class::cast)
                .map(permission -> (CapabilityPermission) permission)
                .map(permission -> permission.getName() + "=" + permission.getActions())
                .toList();

        assertThat(restoredPermissionCollection.implies(new CapabilityPermission("org.example.feature", "provide")))
                .isTrue();
        assertThat(restoredPermissionCollection.implies(new CapabilityPermission("org.example.feature", "require")))
                .isTrue();
        assertThat(restoredPermissionCollection.implies(requestedRequirePermission)).isTrue();
        assertThat(restoredPermissionCollection.implies(mismatchedRequirePermission)).isFalse();
        assertThat(grantedPermissions)
                .containsExactlyInAnyOrder(
                        "*=require,provide",
                        "(&(capability.namespace=osgi.service)(vendor=acme)(name=com.example.bundle)(id=7)(location=file:/bundle))=require");
        assertThatThrownBy(() -> restoredPermissionCollection.add(new CapabilityPermission(
                        "osgi.service",
                        Map.of("vendor", "acme"),
                        bundle,
                        "require")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot add to collection");
        assertThatThrownBy(() -> restoredPermissionCollection.add(new RuntimePermission("exitVM")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid permission");
    }

    private static PermissionCollection serializeAndDeserialize(PermissionCollection permissionCollection)
            throws IOException, ClassNotFoundException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)) {
            objectOutputStream.writeObject(permissionCollection);
        }
        try (ObjectInputStream objectInputStream =
                new ObjectInputStream(new ByteArrayInputStream(byteArrayOutputStream.toByteArray()))) {
            return (PermissionCollection) objectInputStream.readObject();
        }
    }

    private static <K, V> Dictionary<K, V> dictionaryOf(Map<K, V> values) {
        return new Dictionary<>() {
            @Override
            public int size() {
                return values.size();
            }

            @Override
            public boolean isEmpty() {
                return values.isEmpty();
            }

            @Override
            public Enumeration<K> keys() {
                return Collections.enumeration(values.keySet());
            }

            @Override
            public Enumeration<V> elements() {
                return Collections.enumeration(values.values());
            }

            @Override
            public V get(Object key) {
                return values.get(key);
            }

            @Override
            public V put(K key, V value) {
                return values.put(key, value);
            }

            @Override
            public V remove(Object key) {
                return values.remove(key);
            }
        };
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
            return dictionaryOf(new LinkedHashMap<>());
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
        public Dictionary<String, String> getHeaders(String locale) {
            return dictionaryOf(new LinkedHashMap<>());
        }

        @Override
        public String getSymbolicName() {
            return symbolicName;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            throw new ClassNotFoundException(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            return Collections.emptyEnumeration();
        }

        @Override
        public Enumeration<String> getEntryPaths(String path) {
            return Collections.emptyEnumeration();
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
            return Collections.emptyEnumeration();
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
            return Version.emptyVersion;
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
            return Long.compare(bundleId, ((Bundle) other).getBundleId());
        }
    }
}
