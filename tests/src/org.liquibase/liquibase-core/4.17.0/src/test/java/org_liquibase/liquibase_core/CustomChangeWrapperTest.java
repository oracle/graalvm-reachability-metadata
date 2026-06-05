/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.Scope;
import liquibase.change.custom.CustomChange;
import liquibase.change.custom.CustomChangeWrapper;
import liquibase.change.custom.CustomTaskChange;
import liquibase.change.custom.CustomTaskRollback;
import liquibase.database.core.H2Database;
import liquibase.osgi.Activator;
import liquibase.parser.core.ParsedNode;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.ResourceAccessor;
import liquibase.statement.SqlStatement;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
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
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class CustomChangeWrapperTest {

    @Test
    void setClassInstantiatesCustomSqlChangeFromScopeClassLoader() throws Exception {
        CustomChangeWrapper wrapper = new CustomChangeWrapper();

        wrapper.setClass(ExampleCustomSqlChange.class.getName());
        wrapper.setParam("schemaName", "PUBLIC");
        wrapper.setParam("tableName", "person");
        wrapper.setParam("columnName", "employer_id");
        wrapper.setParam("newValue", "3");

        SqlStatement[] statements = wrapper.generateStatements(new H2Database());

        assertThat(wrapper.getCustomChange()).isInstanceOf(ExampleCustomSqlChange.class);
        assertThat(statements).hasSize(1);
        assertThat(statements[0].toString()).contains("person", "employer_id", "3");
    }

    @Test
    void loadParsesCustomChangeAndDiscoversDirectProperties() throws Exception {
        CustomChangeWrapper wrapper = new CustomChangeWrapper();
        ParsedNode parsedNode = new ParsedNode(null, "customChange");
        parsedNode.addChild(null, "class", ExampleCustomSqlChange.class.getName());
        parsedNode.addChild(null, "tableName", "person");
        parsedNode.addChild(null, "columnName", "employer_id");
        parsedNode.addChild(null, "newValue", "4");
        try (ResourceAccessor resourceAccessor = new ClassLoaderResourceAccessor()) {
            wrapper.load(parsedNode, resourceAccessor);
        }

        SqlStatement[] statements = wrapper.generateStatements(new H2Database());

        assertEquals(ExampleCustomSqlChange.class.getName(), wrapper.getClassName());
        assertEquals(Set.of("columnName", "newValue", "tableName"), wrapper.getParams());
        assertThat(statements).hasSize(1);
        assertThat(statements[0].toString()).contains("person", "employer_id", "4");
    }

    @Test
    void setClassUsesOsgiBundleClassLoadingWhenScopeMarksOsgiPlatform() throws Exception {
        Activator activator = new Activator();
        DelegatingBundle bundle = new DelegatingBundle(CustomChangeWrapperTest.class.getClassLoader());
        Activator.LiquibaseBundle liquibaseBundle = activator.addingBundle(bundle, null);
        try {
            Scope.child(Map.of(Scope.Attr.osgiPlatform.name(), true), () -> {
                CustomChangeWrapper wrapper = new CustomChangeWrapper();

                wrapper.setClass(ExampleCustomTaskChange.class.getName());

                assertThat(wrapper.getCustomChange()).isInstanceOf(ExampleCustomTaskChange.class);
                assertEquals("Said Hello", wrapper.getConfirmationMessage());
            });
        } finally {
            activator.removedBundle(bundle, null, liquibaseBundle);
        }
    }

    @Test
    void loadUsesOsgiBundleClassLoadingWhenDiscoveringDirectProperties() throws Exception {
        Activator activator = new Activator();
        DelegatingBundle bundle = new DelegatingBundle(CustomChangeWrapperTest.class.getClassLoader());
        Activator.LiquibaseBundle liquibaseBundle = activator.addingBundle(bundle, null);
        try {
            Scope.child(Map.of(Scope.Attr.osgiPlatform.name(), true), () -> {
                CustomChangeWrapper wrapper = new CustomChangeWrapper();
                ParsedNode parsedNode = new ParsedNode(null, "customChange");
                parsedNode.addChild(null, "class", ExampleCustomTaskChange.class.getName());
                parsedNode.addChild(null, "helloTo", "osgi-world");
                try (ResourceAccessor resourceAccessor = new ClassLoaderResourceAccessor()) {
                    wrapper.load(parsedNode, resourceAccessor);
                }

                SqlStatement[] statements = wrapper.generateStatements(new H2Database());

                assertThat(wrapper.getCustomChange()).isInstanceOf(ExampleCustomTaskChange.class);
                assertEquals(Set.of("helloTo"), wrapper.getParams());
                assertEquals("osgi-world", wrapper.getParamValue("helloTo"));
                assertThat(statements).isEmpty();
            });
        } finally {
            activator.removedBundle(bundle, null, liquibaseBundle);
        }
    }

    @Test
    void setClassFallsBackToThreadContextLoaderAfterIncompatibleScopeClassLoader() throws Exception {
        try {
            ClassLoader originalContextLoader = Thread.currentThread().getContextClassLoader();
            ClassLoader incompatibleLoader = new IncompatibleCustomChangeClassLoader(originalContextLoader);
            try {
                Thread.currentThread().setContextClassLoader(originalContextLoader);
                Scope.child(Map.of(Scope.Attr.classLoader.name(), incompatibleLoader), () -> {
                    CustomChangeWrapper wrapper = new CustomChangeWrapper();

                    wrapper.setClass(ExampleCustomTaskChange.class.getName());

                    assertThat(wrapper.getCustomChange()).isInstanceOf(ExampleCustomTaskChange.class);
                    assertSame(ExampleCustomTaskChange.class, wrapper.getCustomChange().getClass());
                });
            } finally {
                Thread.currentThread().setContextClassLoader(originalContextLoader);
            }
        } catch (Error error) {
            throwIfNotUnsupportedDynamicClassLoading(error);
        }
    }

    @Test
    void setClassFallsBackToCallerClassLoaderWhenContextLoaderCannotFindClass() throws Exception {
        try {
            ClassLoader originalContextLoader = Thread.currentThread().getContextClassLoader();
            ClassLoader incompatibleLoader = new IncompatibleCustomChangeClassLoader(originalContextLoader);
            ClassLoader rejectingContextLoader = new RejectingClassLoader(
                    originalContextLoader,
                    ExampleCustomTaskChange.class.getName()
            );
            try {
                Thread.currentThread().setContextClassLoader(rejectingContextLoader);
                Scope.child(Map.of(Scope.Attr.classLoader.name(), incompatibleLoader), () -> {
                    CustomChangeWrapper wrapper = new CustomChangeWrapper();

                    wrapper.setClass(ExampleCustomTaskChange.class.getName());

                    assertThat(wrapper.getCustomChange()).isInstanceOf(ExampleCustomTaskChange.class);
                    assertSame(ExampleCustomTaskChange.class, wrapper.getCustomChange().getClass());
                });
            } finally {
                Thread.currentThread().setContextClassLoader(originalContextLoader);
            }
        } catch (Error error) {
            throwIfNotUnsupportedDynamicClassLoading(error);
        }
    }

    private static void throwIfNotUnsupportedDynamicClassLoading(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    private static final class IncompatibleCustomChangeClassLoader extends ClassLoader {
        private static final Set<String> ISOLATED_CLASSES = Set.of(
                ExampleCustomTaskChange.class.getName(),
                CustomChange.class.getName(),
                CustomTaskChange.class.getName(),
                CustomTaskRollback.class.getName()
        );

        private IncompatibleCustomChangeClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!ISOLATED_CLASSES.contains(name)) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    byte[] classBytes = readClassBytes(name);
                    loadedClass = defineClass(name, classBytes, 0, classBytes.length);
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        private static byte[] readClassBytes(String className) throws ClassNotFoundException {
            String resourceName = className.replace('.', '/') + ".class";
            ClassLoader loader = CustomChangeWrapperTest.class.getClassLoader();
            try (InputStream inputStream = loader.getResourceAsStream(resourceName)) {
                if (inputStream == null) {
                    throw new ClassNotFoundException(className);
                }
                return inputStream.readAllBytes();
            } catch (IOException exception) {
                ClassNotFoundException classNotFoundException = new ClassNotFoundException(className);
                classNotFoundException.initCause(exception);
                throw classNotFoundException;
            }
        }
    }

    private static final class RejectingClassLoader extends ClassLoader {
        private final String rejectedClassName;

        private RejectingClassLoader(ClassLoader parent, String rejectedClassName) {
            super(parent);
            this.rejectedClassName = rejectedClassName;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (rejectedClassName.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name);
        }
    }

    private static final class DelegatingBundle implements Bundle {
        private final ClassLoader classLoader;
        private final Dictionary<String, String> headers = new Hashtable<>();

        private DelegatingBundle(ClassLoader classLoader) {
            this.classLoader = classLoader;
            headers.put("Liquibase-Custom-Change-Packages", "org_liquibase.liquibase_core");
        }

        @Override
        public int getState() {
            return ACTIVE;
        }

        @Override
        public void start(int options) throws BundleException {
            throw unsupported();
        }

        @Override
        public void start() throws BundleException {
            throw unsupported();
        }

        @Override
        public void stop(int options) throws BundleException {
            throw unsupported();
        }

        @Override
        public void stop() throws BundleException {
            throw unsupported();
        }

        @Override
        public void update(InputStream input) throws BundleException {
            throw unsupported();
        }

        @Override
        public void update() throws BundleException {
            throw unsupported();
        }

        @Override
        public void uninstall() throws BundleException {
            throw unsupported();
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
            return classLoader.getResource(name);
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
            return classLoader.loadClass(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            return classLoader.getResources(name);
        }

        @Override
        public Enumeration<String> getEntryPaths(String path) {
            return Collections.emptyEnumeration();
        }

        @Override
        public URL getEntry(String path) {
            return classLoader.getResource(path);
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

        private static UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("Bundle lifecycle is not used by this test");
        }
    }
}
