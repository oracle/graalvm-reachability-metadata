/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_geronimo_specs.geronimo_osgi_registry;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.apache.geronimo.osgi.registry.Activator;
import org.apache.geronimo.osgi.registry.ProviderRegistryImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;

public class ProviderRegistryImplInnerBundleProviderLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void getServiceCreatesProviderInstancesFromBundleServiceDefinitions() throws Exception {
        Path servicesDirectory = Files.createDirectories(tempDir.resolve("META-INF/services"));
        Path serviceDefinition = servicesDirectory.resolve(TestService.class.getName());
        Files.writeString(serviceDefinition, TestServiceProvider.class.getName(), StandardCharsets.UTF_8);

        TestBundle bundle = new TestBundle(7L, "test.bundle", serviceDefinition.toUri().toURL());
        ProviderRegistryImpl registry = new ProviderRegistryImpl(new Activator());

        Object trackedBundle = registry.addBundle(bundle);
        Object service = registry.getService(TestService.class.getName());

        assertThat(trackedBundle).isNotNull();
        assertThat(service).isInstanceOf(TestServiceProvider.class);
        assertThat(((TestService) service).message()).isEqualTo("created through bundle provider loader");

        registry.removeBundle(bundle, trackedBundle);

        assertThat(registry.getService(TestService.class.getName())).isNull();
    }

    public interface TestService {
        String message();
    }

    public static class TestServiceProvider implements TestService {
        public TestServiceProvider() {
        }

        @Override
        public String message() {
            return "created through bundle provider loader";
        }
    }

    private static final class TestBundle implements Bundle {
        private final long bundleId;
        private final String symbolicName;
        private final Dictionary<String, String> headers = new Hashtable<>();
        private final URL serviceDefinition;

        private TestBundle(long bundleId, String symbolicName, URL serviceDefinition) {
            this.bundleId = bundleId;
            this.symbolicName = symbolicName;
            this.serviceDefinition = serviceDefinition;
            headers.put("SPI-Provider", "enabled");
        }

        @Override
        public int getState() {
            throw new UnsupportedOperationException();
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
            return headers;
        }

        @Override
        public long getBundleId() {
            return bundleId;
        }

        @Override
        public String getLocation() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ServiceReference[] getRegisteredServices() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ServiceReference[] getServicesInUse() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasPermission(Object permission) {
            throw new UnsupportedOperationException();
        }

        @Override
        public URL getResource(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Dictionary<String, String> getHeaders(String locale) {
            return headers;
        }

        @Override
        public String getSymbolicName() {
            return symbolicName;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            return Class.forName(name);
        }

        @Override
        public Enumeration getResources(String name) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Enumeration getEntryPaths(String path) {
            throw new UnsupportedOperationException();
        }

        @Override
        public URL getEntry(String path) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getLastModified() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Enumeration findEntries(String path, String filePattern, boolean recurse) {
            if ("META-INF/services/".equals(path)) {
                return Collections.enumeration(List.of(serviceDefinition));
            }
            return null;
        }

        @Override
        public BundleContext getBundleContext() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map getSignerCertificates(int signersType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Version getVersion() {
            throw new UnsupportedOperationException();
        }
    }
}
