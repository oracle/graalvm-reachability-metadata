/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.Scope;
import liquibase.change.custom.CustomChangeWrapper;
import liquibase.exception.CustomChangeException;
import liquibase.osgi.Activator;
import liquibase.parser.core.ParsedNode;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CustomChangeWrapperTest {

    @Test
    void setClassCreatesCustomChangeWithScopedClassLoader() throws Exception {
        CustomChangeWrapper wrapper = new CustomChangeWrapper();

        CustomChangeWrapper result = wrapper.setClass(ExampleCustomTaskChange.class.getName());

        assertThat(result).isSameAs(wrapper);
        assertThat(wrapper.getClassName()).isEqualTo(ExampleCustomTaskChange.class.getName());
        assertThat(wrapper.getCustomChange()).isInstanceOf(ExampleCustomTaskChange.class);
    }

    @Test
    void setClassCreatesCustomChangeWithOsgiClassLoader() throws Exception {
        CustomChangeWrapper wrapper = new CustomChangeWrapper();
        Activator activator = new Activator();
        TestBundle bundle = new TestBundle();
        Activator.LiquibaseBundle liquibaseBundle = activator.addingBundle(bundle, null);

        try {
            Scope.child(Scope.Attr.osgiPlatform, true, () -> wrapper.setClass(ExampleCustomTaskChange.class.getName()));
        } finally {
            activator.removedBundle(bundle, null, liquibaseBundle);
        }

        assertThat(wrapper.getClassName()).isEqualTo(ExampleCustomTaskChange.class.getName());
        assertThat(wrapper.getCustomChange()).isInstanceOf(ExampleCustomTaskChange.class);
    }

    @Test
    void setClassFallsBackToThreadContextClassLoaderAfterClassCastFailure() {
        CustomChangeWrapper wrapper = new CustomChangeWrapper();
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();

        Thread.currentThread().setContextClassLoader(CustomChangeWrapperTest.class.getClassLoader());
        try {
            assertThatThrownBy(() -> wrapper.setClass(DatabaseFactoryTest.class.getName()))
                    .isInstanceOf(CustomChangeException.class)
                    .hasCauseInstanceOf(ClassCastException.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    void setClassFallsBackToDefaultClassForNameWhenThreadContextClassLoaderCannotLoadClass() throws Exception {
        CustomChangeWrapper wrapper = new CustomChangeWrapper();
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader scopedClassLoader = CustomChangeWrapperTest.class.getClassLoader();
        ClassLoader blockingContextClassLoader = new BlockingClassLoader(
                originalContextClassLoader,
                DatabaseFactoryTest.class.getName()
        );

        Scope.child(Scope.Attr.classLoader, scopedClassLoader, () -> {
            Thread.currentThread().setContextClassLoader(blockingContextClassLoader);
            try {
                assertThatThrownBy(() -> wrapper.setClass(DatabaseFactoryTest.class.getName()))
                        .isInstanceOf(CustomChangeException.class)
                        .hasCauseInstanceOf(ClassCastException.class);
            } finally {
                Thread.currentThread().setContextClassLoader(originalContextClassLoader);
            }
        });
    }

    @Test
    void customLoadLogicInstantiatesCustomChangeAndConvertsMatchingNodesToParams() throws Exception {
        CustomChangeWrapper wrapper = new CustomChangeWrapper()
                .setClass(ExampleCustomTaskChange.class.getName());
        ParsedNode parsedNode = new ParsedNode(null, "customChange")
                .addChild(null, "helloTo", "Liquibase")
                .addChild(new ParsedNode(null, "param")
                        .addChild(null, "name", "anotherProperty")
                        .addChild(null, "value", "anotherValue"));

        wrapper.customLoadLogic(parsedNode, null);

        assertThat(wrapper.getParamValue("helloTo")).isEqualTo("Liquibase");
        assertThat(wrapper.getParamValue("anotherProperty")).isEqualTo("anotherValue");
    }
}

final class TestBundle implements Bundle {

    private final Dictionary<String, String> headers = FrameworkUtil.asDictionary(new HashMap<>());

    TestBundle() {
        headers.put("Liquibase-Custom-Change-Packages", "*");
    }

    @Override
    public int getState() {
        return ACTIVE;
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
        return 1L;
    }

    @Override
    public String getLocation() {
        return "test-bundle";
    }

    @Override
    public ServiceReference<?>[] getRegisteredServices() {
        return new ServiceReference<?>[0];
    }

    @Override
    public ServiceReference<?>[] getServicesInUse() {
        return new ServiceReference<?>[0];
    }

    @Override
    public boolean hasPermission(Object permission) {
        return true;
    }

    @Override
    public URL getResource(String name) {
        return CustomChangeWrapperTest.class.getClassLoader().getResource(name);
    }

    @Override
    public Dictionary<String, String> getHeaders(String locale) {
        return headers;
    }

    @Override
    public String getSymbolicName() {
        return "org-liquibase-test-bundle";
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        return CustomChangeWrapperTest.class.getClassLoader().loadClass(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        return CustomChangeWrapperTest.class.getClassLoader().getResources(name);
    }

    @Override
    public Enumeration<String> getEntryPaths(String path) {
        return null;
    }

    @Override
    public URL getEntry(String path) {
        return getResource(path);
    }

    @Override
    public long getLastModified() {
        return 0L;
    }

    @Override
    public Enumeration<URL> findEntries(String path, String filePattern, boolean recurse) {
        return null;
    }

    @Override
    public BundleContext getBundleContext() {
        return null;
    }

    @Override
    public Map<X509Certificate, List<X509Certificate>> getSignerCertificates(int signersType) {
        return Collections.emptyMap();
    }

    @Override
    public Version getVersion() {
        return Version.emptyVersion;
    }

    @Override
    public <A> A adapt(Class<A> type) {
        return null;
    }

    @Override
    public File getDataFile(String filename) {
        return null;
    }

    @Override
    public int compareTo(Bundle other) {
        return Long.compare(getBundleId(), other.getBundleId());
    }
}

final class BlockingClassLoader extends ClassLoader {

    private final String blockedClassName;

    BlockingClassLoader(ClassLoader parent, String blockedClassName) {
        super(parent);
        this.blockedClassName = blockedClassName;
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        if (blockedClassName.equals(name)) {
            throw new ClassNotFoundException(name);
        }
        return super.loadClass(name);
    }
}
