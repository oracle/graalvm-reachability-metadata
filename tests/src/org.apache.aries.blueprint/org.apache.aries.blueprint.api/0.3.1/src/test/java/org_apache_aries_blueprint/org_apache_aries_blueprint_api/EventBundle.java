/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_aries_blueprint.org_apache_aries_blueprint_api;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;

import java.io.InputStream;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

/// Identity-only bundle handle: Blueprint events retain bundles without invoking them.
final class EventBundle implements Bundle {
    @Override
    public int getState() {
        throw new UnsupportedOperationException();
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
    public Dictionary<String, String> getHeaders() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getBundleId() {
        throw new UnsupportedOperationException();
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
        throw new UnsupportedOperationException();
    }

    @Override
    public String getSymbolicName() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Class<?> loadClass(String name) {
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
    public long getLastModified() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Enumeration<URL> findEntries(String path, String pattern, boolean recurse) {
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
