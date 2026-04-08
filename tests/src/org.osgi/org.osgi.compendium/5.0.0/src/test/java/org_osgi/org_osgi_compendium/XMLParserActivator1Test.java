/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_compendium;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.Version;
import org.osgi.util.xml.XMLParserActivator;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class XMLParserActivator1Test {
    @TempDir
    Path tempDir;

    @BeforeEach
    void resetFactoryState() {
        TestDocumentBuilderFactory.reset();
    }

    @Test
    void startInstantiatesConfiguredDocumentBuilderFactoryClass() throws Exception {
        Path servicesDirectory = tempDir.resolve("META-INF/services");
        Files.createDirectories(servicesDirectory);
        Path domFactoryServiceFile = servicesDirectory.resolve(XMLParserActivator.DOMFACTORYNAME);
        Files.writeString(
                domFactoryServiceFile,
                TestDocumentBuilderFactory.class.getName() + System.lineSeparator());

        TestBundle bundle = new TestBundle(17L);
        bundle.addResource(XMLParserActivator.DOMCLASSFILE, domFactoryServiceFile.toUri().toURL());
        TestBundleContext context = new TestBundleContext(bundle);
        XMLParserActivator activator = new XMLParserActivator();

        activator.start(context);

        assertThat(TestDocumentBuilderFactory.constructorCalls).isEqualTo(1);
        assertThat(context.registrationClasses).containsExactly(XMLParserActivator.DOMFACTORYNAME);
        assertThat(context.registrationProperties).hasSize(1);
        Map<String, Object> properties = context.registrationProperties.get(0);
        assertThat(properties.get(Constants.SERVICE_DESCRIPTION))
                .isEqualTo("A JAXP Compliant DOM Parser");
        assertThat(properties.get(Constants.SERVICE_PID))
                .isEqualTo("javax.xml.parsers.DocumentBuilderFactory.17.0");
        assertThat(properties.get(XMLParserActivator.PARSER_VALIDATING)).isEqualTo(Boolean.TRUE);
        assertThat(properties.get(XMLParserActivator.PARSER_NAMESPACEAWARE)).isEqualTo(Boolean.TRUE);
        assertThat(properties.get("parser.factoryname"))
                .isEqualTo(TestDocumentBuilderFactory.class.getName());
    }

    public static final class TestDocumentBuilderFactory extends DocumentBuilderFactory {
        private static int constructorCalls;

        public TestDocumentBuilderFactory() {
            constructorCalls++;
        }

        static void reset() {
            constructorCalls = 0;
        }

        @Override
        public DocumentBuilder newDocumentBuilder() {
            return new TestDocumentBuilder(isNamespaceAware(), isValidating());
        }

        @Override
        public void setAttribute(String name, Object value) {
        }

        @Override
        public Object getAttribute(String name) {
            return null;
        }

        @Override
        public void setFeature(String name, boolean value) {
        }

        @Override
        public boolean getFeature(String name) {
            return false;
        }
    }

    private static final class TestDocumentBuilder extends DocumentBuilder {
        private final boolean namespaceAware;
        private final boolean validating;

        private TestDocumentBuilder(boolean namespaceAware, boolean validating) {
            this.namespaceAware = namespaceAware;
            this.validating = validating;
        }

        @Override
        public Document parse(InputSource is) throws SAXException, IOException {
            throw new UnsupportedOperationException("parse is not used by this coverage test");
        }

        @Override
        public boolean isNamespaceAware() {
            return namespaceAware;
        }

        @Override
        public boolean isValidating() {
            return validating;
        }

        @Override
        public void setEntityResolver(EntityResolver er) {
        }

        @Override
        public void setErrorHandler(ErrorHandler eh) {
        }

        @Override
        public Document newDocument() {
            return null;
        }

        @Override
        public DOMImplementation getDOMImplementation() {
            return null;
        }
    }

    private static final class TestBundleContext implements BundleContext {
        private final TestBundle bundle;
        private final List<String> registrationClasses = new ArrayList<>();
        private final List<Map<String, Object>> registrationProperties =
                new ArrayList<>();

        private TestBundleContext(TestBundle bundle) {
            this.bundle = bundle;
        }

        @Override
        public String getProperty(String key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Bundle getBundle() {
            return bundle;
        }

        @Override
        public Bundle installBundle(String location, InputStream input) throws BundleException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Bundle installBundle(String location) throws BundleException {
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
        public void addServiceListener(ServiceListener listener, String filter)
                throws InvalidSyntaxException {
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
        public ServiceRegistration<?> registerService(
                String[] clazzes, Object service, Dictionary<String, ?> properties) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ServiceRegistration<?> registerService(
                String clazz, Object service, Dictionary<String, ?> properties) {
            registrationClasses.add(clazz);
            registrationProperties.add(copyDictionary(properties));
            return new TestServiceRegistration();
        }

        @Override
        public <S> ServiceRegistration<S> registerService(
                Class<S> clazz, S service, Dictionary<String, ?> properties) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ServiceReference<?>[] getServiceReferences(String clazz, String filter)
                throws InvalidSyntaxException {
            throw new UnsupportedOperationException();
        }

        @Override
        public ServiceReference<?>[] getAllServiceReferences(String clazz, String filter)
                throws InvalidSyntaxException {
            throw new UnsupportedOperationException();
        }

        @Override
        public ServiceReference<?> getServiceReference(String clazz) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S> ServiceReference<S> getServiceReference(Class<S> clazz) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S> Collection<ServiceReference<S>> getServiceReferences(Class<S> clazz, String filter)
                throws InvalidSyntaxException {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S> S getService(ServiceReference<S> reference) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean ungetService(ServiceReference<?> reference) {
            throw new UnsupportedOperationException();
        }

        @Override
        public File getDataFile(String filename) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Filter createFilter(String filter) throws InvalidSyntaxException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Bundle getBundle(String location) {
            throw new UnsupportedOperationException();
        }

        private static Map<String, Object> copyDictionary(Dictionary<String, ?> properties) {
            Map<String, Object> copiedProperties = new HashMap<>();
            Enumeration<String> keys = properties.keys();
            while (keys.hasMoreElements()) {
                String key = keys.nextElement();
                copiedProperties.put(key, properties.get(key));
            }
            return copiedProperties;
        }
    }

    private static final class TestServiceRegistration implements ServiceRegistration<Object> {
        @Override
        public ServiceReference<Object> getReference() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setProperties(Dictionary<String, ?> properties) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void unregister() {
            throw new UnsupportedOperationException();
        }
    }

    private static final class TestBundle implements Bundle {
        private final long bundleId;
        private final Map<String, URL> resources = new HashMap<>();

        private TestBundle(long bundleId) {
            this.bundleId = bundleId;
        }

        private void addResource(String path, URL resource) {
            resources.put(path, resource);
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
            throw new UnsupportedOperationException();
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
        public ServiceReference<?>[] getRegisteredServices() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ServiceReference<?>[] getServicesInUse() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasPermission(Object permission) {
            throw new UnsupportedOperationException();
        }

        @Override
        public URL getResource(String name) {
            return resources.get(name);
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
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            return Class.forName(name);
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

        @Override
        public <A> A adapt(Class<A> type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public File getDataFile(String filename) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int compareTo(Bundle other) {
            return Long.compare(bundleId, other.getBundleId());
        }
    }
}
