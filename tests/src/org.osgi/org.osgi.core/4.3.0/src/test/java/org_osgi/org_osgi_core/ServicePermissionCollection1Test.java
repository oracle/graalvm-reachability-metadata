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
import java.util.Collection;
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
import org.osgi.framework.Constants;
import org.osgi.framework.ServicePermission;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;

public class ServicePermissionCollection1Test {
    @Test
    void permissionCollectionInitializesAtRunTimeAndMatchesNamedAndFilteredRequests() throws Exception {
        PermissionCollection wildcardPermissionCollection = new ServicePermission("com.example.*", "register")
                .newPermissionCollection();
        wildcardPermissionCollection.add(new ServicePermission("com.example.*", "get"));
        wildcardPermissionCollection.add(new ServicePermission("com.example.*", "register"));
        wildcardPermissionCollection.add(new ServicePermission("*", "get"));

        PermissionCollection restoredWildcardPermissionCollection =
                serializeAndDeserialize(wildcardPermissionCollection);
        TestBundle bundle = new TestBundle(7L, "com.example.bundle", "file:/bundle");
        TestServiceReference matchingReference = new TestServiceReference(
                bundle,
                Map.of(
                        Constants.OBJECTCLASS,
                        new String[] {"com.example.WidgetService", "com.example.AuditService"},
                        Constants.SERVICE_ID,
                        11L,
                        "mode",
                        "async"));
        TestServiceReference otherReference = new TestServiceReference(
                bundle,
                Map.of(
                        Constants.OBJECTCLASS,
                        new String[] {"org.example.WidgetService"},
                        Constants.SERVICE_ID,
                        12L,
                        "mode",
                        "sync"));
        ServicePermission requestedGetPermission = new ServicePermission(matchingReference, "get");
        ServicePermission requestedRegisterPermission = new ServicePermission("com.example.WidgetService", "register");
        PermissionCollection filteredPermissionCollection =
                new ServicePermission("(&(objectClass=com.example.WidgetService)(mode=async))", "get")
                        .newPermissionCollection();
        filteredPermissionCollection.add(new ServicePermission(
                "(&(objectClass=com.example.WidgetService)(mode=async))",
                "get"));
        List<String> grantedPermissions = Collections.list(restoredWildcardPermissionCollection.elements()).stream()
                .map(Permission.class::cast)
                .map(permission -> (ServicePermission) permission)
                .map(permission -> permission.getName() + "=" + permission.getActions())
                .toList();

        assertThat(restoredWildcardPermissionCollection.implies(requestedGetPermission)).isTrue();
        assertThat(restoredWildcardPermissionCollection.implies(requestedRegisterPermission)).isTrue();
        assertThat(restoredWildcardPermissionCollection.implies(new ServicePermission(otherReference, "get")))
                .isTrue();
        assertThat(restoredWildcardPermissionCollection.implies(new ServicePermission("org.example.WidgetService", "register")))
                .isFalse();
        assertThat(filteredPermissionCollection.implies(requestedGetPermission)).isTrue();
        assertThat(filteredPermissionCollection.implies(new ServicePermission(otherReference, "get")))
                .isFalse();
        assertThat(grantedPermissions)
                .containsExactlyInAnyOrder("*=get", "com.example.*=get,register");
        assertThatThrownBy(
                        () -> restoredWildcardPermissionCollection.add(new ServicePermission(matchingReference, "get")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot add to collection");
        assertThatThrownBy(() -> restoredWildcardPermissionCollection.add(new RuntimePermission("exitVM")))
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

    private static final class TestServiceReference implements ServiceReference {
        private final Bundle bundle;
        private final Map<String, Object> properties;

        private TestServiceReference(Bundle bundle, Map<String, Object> properties) {
            this.bundle = bundle;
            this.properties = new LinkedHashMap<>(properties);
        }

        @Override
        public Object getProperty(String key) {
            return properties.get(key);
        }

        @Override
        public String[] getPropertyKeys() {
            Collection<String> keys = properties.keySet();
            return keys.toArray(new String[0]);
        }

        @Override
        public Bundle getBundle() {
            return bundle;
        }

        @Override
        public Bundle[] getUsingBundles() {
            return new Bundle[0];
        }

        @Override
        public boolean isAssignableTo(Bundle bundle, String className) {
            return true;
        }

        @Override
        public int compareTo(Object other) {
            if (!(other instanceof ServiceReference)) {
                return 1;
            }
            Object thisId = getProperty(Constants.SERVICE_ID);
            Object otherId = ((ServiceReference) other).getProperty(Constants.SERVICE_ID);
            return Long.compare(((Number) thisId).longValue(), ((Number) otherId).longValue());
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
}
