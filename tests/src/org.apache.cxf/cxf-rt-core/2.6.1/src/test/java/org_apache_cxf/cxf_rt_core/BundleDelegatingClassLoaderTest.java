/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_cxf.cxf_rt_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import org.apache.cxf.bus.blueprint.BundleDelegatingClassLoader;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;

public class BundleDelegatingClassLoaderTest {
    @Test
    void fallsBackToApplicationClassLoaderForClassesAndResources() throws ClassNotFoundException {
        Bundle bundle = new EmptyBundle();
        ClassLoader applicationClassLoader = getClass().getClassLoader();
        BundleDelegatingClassLoader loader =
                new BundleDelegatingClassLoader(bundle, applicationClassLoader);

        Class<?> loaded = loader.loadClass(BundleDelegatingClassLoader.class.getName());
        URL resource = loader.getResource("META-INF/wsdl.plugin.xml");

        assertThat(loaded).isSameAs(BundleDelegatingClassLoader.class);
        assertThat(resource)
                .isNotNull()
                .isEqualTo(applicationClassLoader.getResource("META-INF/wsdl.plugin.xml"));
        assertThat(loader.getBundle()).isSameAs(bundle);
    }

    private static final class EmptyBundle implements Bundle {
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
            throw new UnsupportedOperationException();
        }

        @Override
        public long getBundleId() {
            return 1L;
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
            return null;
        }

        @Override
        public Dictionary<String, String> getHeaders(String locale) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getSymbolicName() {
            return "org.example.empty";
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            throw new ClassNotFoundException(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Enumeration<String> getEntryPaths(String path) {
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
        public Enumeration<URL> findEntries(String path, String filePattern, boolean recurse) {
            throw new UnsupportedOperationException();
        }

        @Override
        public BundleContext getBundleContext() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<X509Certificate, List<X509Certificate>> getSignerCertificates(int signersType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Version getVersion() {
            throw new UnsupportedOperationException();
        }
    }
}
