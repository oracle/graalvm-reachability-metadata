/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_aries_quiesce.org_apache_aries_quiesce_api;

import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Dictionary;

import org.apache.aries.quiesce.participant.QuiesceParticipant;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

/// Supplies statically constructed participant services; unrelated framework operations are unsupported.
final class QuiesceTestContext implements BundleContext {
    private final ParticipantReference[] references;

    QuiesceTestContext(QuiesceParticipant... participants) {
        references = Arrays.stream(participants).map(ParticipantReference::new).toArray(ParticipantReference[]::new);
    }

    @Override
    public ServiceReference[] getServiceReferences(String clazz, String filter) {
        if (!QuiesceParticipant.class.getName().equals(clazz) || filter != null) {
            throw new IllegalArgumentException("Only unfiltered quiesce participants are available");
        }
        return references.length == 0 ? null : references.clone();
    }

    @Override
    public Object getService(ServiceReference reference) {
        return ((ParticipantReference) reference).participant;
    }

    @Override
    public String getProperty(String key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Bundle getBundle() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Bundle installBundle(String location, InputStream input) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Bundle installBundle(String location) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Bundle getBundle(long id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Bundle[] getBundles() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addServiceListener(ServiceListener listener, String filter) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addServiceListener(ServiceListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeServiceListener(ServiceListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addBundleListener(BundleListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeBundleListener(BundleListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addFrameworkListener(FrameworkListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeFrameworkListener(FrameworkListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ServiceRegistration registerService(String[] clazzes, Object service, Dictionary properties) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ServiceRegistration registerService(String clazz, Object service, Dictionary properties) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ServiceReference[] getAllServiceReferences(String clazz, String filter) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ServiceReference getServiceReference(String clazz) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean ungetService(ServiceReference reference) {
        throw new UnsupportedOperationException();
    }

    @Override
    public File getDataFile(String filename) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Filter createFilter(String filter) {
        throw new UnsupportedOperationException();
    }

    private static final class ParticipantReference implements ServiceReference {
        private final QuiesceParticipant participant;

        private ParticipantReference(QuiesceParticipant participant) {
            this.participant = participant;
        }

        @Override
        public Object getProperty(String key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String[] getPropertyKeys() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Bundle getBundle() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Bundle[] getUsingBundles() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isAssignableTo(Bundle bundle, String className) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int compareTo(Object reference) {
            throw new UnsupportedOperationException();
        }
    }
}
