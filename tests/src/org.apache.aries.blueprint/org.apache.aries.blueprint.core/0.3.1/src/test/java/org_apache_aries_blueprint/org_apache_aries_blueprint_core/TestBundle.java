/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_aries_blueprint.org_apache_aries_blueprint_core;

import java.io.InputStream;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;

/// An active host bundle for registry-only tests; installation and class loading are not supported.
final class TestBundle implements Bundle {
    private final BundleContext context;

    TestBundle(BundleContext context) {
        this.context = context;
    }

    @Override
    public int getState() {
        return ACTIVE;
    }

    @Override
    public long getBundleId() {
        return 1;
    }

    @Override
    public String getSymbolicName() {
        return "blueprint-test-host";
    }

    @Override
    public Version getVersion() {
        return Version.emptyVersion;
    }

    @Override
    public BundleContext getBundleContext() {
        return context;
    }

    @Override
    public Dictionary<String, String> getHeaders() {
        return new Hashtable<>();
    }

    @Override
    public Dictionary<String, String> getHeaders(String locale) {
        return getHeaders();
    }

    @Override
    public String getLocation() {
        return "blueprint-test-host";
    }

    @Override
    public boolean hasPermission(Object permission) {
        return true;
    }

    @Override
    public long getLastModified() {
        return 0;
    }

    @Override
    public Map<X509Certificate, List<X509Certificate>> getSignerCertificates(int type) {
        return Map.of();
    }

    @Override
    public ServiceReference[] getRegisteredServices() {
        return context.getBundle() == this ? contextServices() : null;
    }

    private ServiceReference[] contextServices() {
        try {
            return context.getServiceReferences(null, null);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Override
    public ServiceReference[] getServicesInUse() {
        return null;
    }

    @Override
    public void start(int options) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void start() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void stop(int options) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void stop() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void update(InputStream input) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void update() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void uninstall() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        throw new ClassNotFoundException(name);
    }

    @Override
    public URL getResource(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Enumeration<URL> getResources(String name) {
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
    public Enumeration<URL> findEntries(String path, String pattern, boolean recurse) {
        throw new UnsupportedOperationException();
    }
}
