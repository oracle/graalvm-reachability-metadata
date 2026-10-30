/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_aries_quiesce.org_apache_aries_quiesce_api;

import java.io.InputStream;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;

/// A statically defined bundle whose stop operation can be observed without a framework.
final class QuiesceTestBundle implements Bundle {
    private final CountDownLatch stopped = new CountDownLatch(1);
    private final AtomicInteger stopCount = new AtomicInteger();
    private volatile int state = ACTIVE;

    int stopCount() {
        return stopCount.get();
    }

    boolean awaitStop() throws InterruptedException {
        return stopped.await(10, TimeUnit.SECONDS);
    }

    @Override
    public int getState() {
        return state;
    }

    @Override
    public void stop() {
        stopCount.incrementAndGet();
        state = RESOLVED;
        stopped.countDown();
    }

    @Override
    public void stop(int options) {
        stop();
    }

    @Override
    public String getSymbolicName() {
        return "test.quiesce.bundle";
    }

    @Override
    public void start() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void start(int options) {
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
    public Dictionary<String, String> getHeaders(String locale) {
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
