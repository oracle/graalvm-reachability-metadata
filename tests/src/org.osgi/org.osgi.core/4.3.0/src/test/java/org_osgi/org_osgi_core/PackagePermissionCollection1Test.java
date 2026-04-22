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
import org.osgi.framework.PackagePermission;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;

public class PackagePermissionCollection1Test {
    @Test
    void permissionCollectionSerializesAndMatchesFilteredImportAndExportOnlyPermissions() throws Exception {
        TestBundle exportingBundle = new TestBundle(11L, "com.example.exporter", "file:/bundles/exporter");
        TestBundle otherBundle = new TestBundle(12L, "com.example.other", "file:/bundles/other");
        PackagePermission filteredImportPermission = new PackagePermission(
                "(&(package.name=com.example.api)(name=com.example.exporter)(location=file:/bundles/exporter)(id=11))",
                "import");
        PackagePermission exportOnlyPermission = new PackagePermission("com.example.api.*", "exportonly");
        PermissionCollection permissionCollection = filteredImportPermission.newPermissionCollection();
        permissionCollection.add(filteredImportPermission);
        permissionCollection.add(exportOnlyPermission);

        PermissionCollection restoredPermissionCollection = serializeAndDeserialize(permissionCollection);
        PackagePermission requestedImportPermission = new PackagePermission("com.example.api", exportingBundle, "import");
        PackagePermission mismatchedImportPermission = new PackagePermission("com.example.api", otherBundle, "import");
        List<String> grantedPermissions = Collections.list(restoredPermissionCollection.elements()).stream()
                .map(Permission.class::cast)
                .map(permission -> (PackagePermission) permission)
                .map(permission -> permission.getName() + "=" + permission.getActions())
                .toList();

        assertThat(restoredPermissionCollection.implies(requestedImportPermission)).isTrue();
        assertThat(restoredPermissionCollection.implies(mismatchedImportPermission)).isFalse();
        assertThat(restoredPermissionCollection.implies(new PackagePermission("com.example.api.internal", "exportonly")))
                .isTrue();
        assertThat(restoredPermissionCollection.implies(new PackagePermission("com.example.api.internal", "import")))
                .isFalse();
        assertThat(grantedPermissions)
                .containsExactlyInAnyOrder(
                        "(&(package.name=com.example.api)(name=com.example.exporter)(location=file:/bundles/exporter)(id=11))=import",
                        "com.example.api.*=exportonly");
        assertThatThrownBy(
                        () -> restoredPermissionCollection.add(new PackagePermission("com.example.api", exportingBundle, "import")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot add to collection");
        assertThatThrownBy(() -> new PackagePermission("(package.name=com.example.api)", "exportonly"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid action string for filter expression");
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
