/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_aries_blueprint.org_apache_aries_blueprint_core;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

/// A single-bundle service registry used without installing bundles or creating class loaders.
final class TestBundleContext implements BundleContext {
    private final Bundle bundle = new TestBundle(this);
    private final Map<ServiceListener, Filter> listeners = new LinkedHashMap<>();
    private final List<Registration> registrations = new ArrayList<>();
    private long nextId;

    @Override
    public Bundle getBundle() {
        return bundle;
    }

    @Override
    public Bundle getBundle(long id) {
        return id == bundle.getBundleId() ? bundle : null;
    }

    @Override
    public Bundle[] getBundles() {
        return new Bundle[]{bundle};
    }

    @Override
    public String getProperty(String name) {
        return null;
    }

    @Override
    public void addServiceListener(ServiceListener listener, String filter) throws InvalidSyntaxException {
        listeners.put(listener, filter == null ? null : createFilter(filter));
    }

    @Override
    public void addServiceListener(ServiceListener listener) {
        listeners.put(listener, null);
    }

    @Override
    public void removeServiceListener(ServiceListener listener) {
        listeners.remove(listener);
    }

    @Override
    public Filter createFilter(String expression) throws InvalidSyntaxException {
        return FrameworkUtil.createFilter(expression);
    }

    @Override
    public ServiceRegistration registerService(String[] classes, Object service, Dictionary properties) {
        Registration registration = new Registration(classes, service, properties, ++nextId);
        registrations.add(registration);
        notifyListeners(ServiceEvent.REGISTERED, registration.reference);
        return registration;
    }

    @Override
    public ServiceRegistration registerService(String type, Object service, Dictionary properties) {
        return registerService(new String[]{type}, service, properties);
    }

    private void notifyListeners(int type, ServiceReference reference) {
        for (Map.Entry<ServiceListener, Filter> entry : new LinkedHashMap<>(listeners).entrySet()) {
            if (entry.getValue() == null || entry.getValue().match(reference)) {
                entry.getKey().serviceChanged(new ServiceEvent(type, reference));
            }
        }
    }

    @Override
    public ServiceReference[] getServiceReferences(String type, String filter) throws InvalidSyntaxException {
        Filter matcher = filter == null ? null : createFilter(filter);
        List<ServiceReference> references = new ArrayList<>();
        for (Registration registration : registrations) {
            if ((type == null || registration.classes.contains(type))
                    && (matcher == null || matcher.match(registration.reference))) {
                references.add(registration.reference);
            }
        }
        return references.isEmpty() ? null : references.toArray(new ServiceReference[0]);
    }

    @Override
    public ServiceReference[] getAllServiceReferences(String type, String filter) throws InvalidSyntaxException {
        return getServiceReferences(type, filter);
    }

    @Override
    public ServiceReference getServiceReference(String type) {
        for (Registration registration : registrations) {
            if (registration.classes.contains(type)) {
                return registration.reference;
            }
        }
        return null;
    }

    @Override
    public Object getService(ServiceReference reference) {
        for (Registration registration : registrations) {
            if (registration.reference == reference) {
                return registration.service;
            }
        }
        return null;
    }

    @Override
    public boolean ungetService(ServiceReference reference) {
        return getService(reference) != null;
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
    public File getDataFile(String name) {
        throw new UnsupportedOperationException();
    }

    private final class Registration implements ServiceRegistration {
        private final Set<String> classes;
        private final Object service;
        private final long id;
        private final Reference reference = new Reference();
        private final Map<String, Object> properties = new LinkedHashMap<>();

        private Registration(String[] classes, Object service, Dictionary properties, long id) {
            this.classes = new LinkedHashSet<>(Arrays.asList(classes));
            this.service = service;
            this.id = id;
            copyProperties(properties);
        }

        private void copyProperties(Dictionary source) {
            properties.clear();
            if (source != null) {
                for (Enumeration<?> keys = source.keys(); keys.hasMoreElements();) {
                    String key = (String) keys.nextElement();
                    properties.put(key, source.get(key));
                }
            }
            properties.put(Constants.OBJECTCLASS, classes.toArray(new String[0]));
            properties.put(Constants.SERVICE_ID, id);
        }

        @Override
        public ServiceReference getReference() {
            return reference;
        }

        @Override
        public void setProperties(Dictionary values) {
            copyProperties(values);
            notifyListeners(ServiceEvent.MODIFIED, reference);
        }

        @Override
        public void unregister() {
            notifyListeners(ServiceEvent.UNREGISTERING, reference);
            registrations.remove(this);
        }

        private final class Reference implements ServiceReference {
            @Override
            public Object getProperty(String name) {
                return properties.get(name);
            }

            @Override
            public String[] getPropertyKeys() {
                return properties.keySet().toArray(new String[0]);
            }

            @Override
            public Bundle getBundle() {
                return registrations.contains(Registration.this) ? bundle : null;
            }

            @Override
            public Bundle[] getUsingBundles() {
                return null;
            }

            @Override
            public boolean isAssignableTo(Bundle consumer, String name) {
                return consumer == bundle && classes.contains(name);
            }

            @Override
            public int compareTo(Object other) {
                ServiceReference that = (ServiceReference) other;
                return Long.compare(id, (Long) that.getProperty(Constants.SERVICE_ID));
            }
        }
    }
}
