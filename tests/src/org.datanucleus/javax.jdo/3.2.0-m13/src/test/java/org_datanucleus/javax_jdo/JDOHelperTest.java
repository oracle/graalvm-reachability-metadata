/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_datanucleus.javax_jdo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.IllegalClassFormatException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Properties;

import javax.jdo.JDOEnhancer;
import javax.jdo.JDOHelper;
import javax.jdo.metadata.JDOMetadata;

import org.junit.jupiter.api.Test;

public class JDOHelperTest {

    private static final String ENHANCER_SERVICE_NAME = "META-INF/services/javax.jdo.JDOEnhancer";

    @Test
    void getEnhancerInstantiatesServiceProvider() {
        TestEnhancer.reset();
        ClassLoader loader = new InMemoryServiceClassLoader(
                getClass().getClassLoader(),
                ENHANCER_SERVICE_NAME,
                TestEnhancer.class
        );

        JDOEnhancer enhancer = JDOHelper.getEnhancer(loader);

        assertThat(enhancer).isInstanceOf(TestEnhancer.class);
        assertThat(TestEnhancer.instantiationCount).isEqualTo(1);
        assertThat(enhancer.getProperties())
                .containsEntry("VendorName", "test-enhancer")
                .containsEntry("VersionNumber", "test-version");
    }

    private static final class InMemoryServiceClassLoader extends ClassLoader {

        private final String serviceName;
        private final String implementationClassName;

        private InMemoryServiceClassLoader(
                ClassLoader parent,
                String serviceName,
                Class<?> implementationClass
        ) {
            super(parent);
            this.serviceName = serviceName;
            this.implementationClassName = implementationClass.getName();
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (!serviceName.equals(name)) {
                return super.getResources(name);
            }
            return Collections.enumeration(Collections.singleton(createServiceUrl()));
        }

        private URL createServiceUrl() throws IOException {
            byte[] contents = (implementationClassName + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
            return new URL(null, "memory:jdo-enhancer-service", new URLStreamHandler() {
                @Override
                protected URLConnection openConnection(URL url) {
                    return new URLConnection(url) {
                        @Override
                        public void connect() {
                        }

                        @Override
                        public InputStream getInputStream() {
                            return new ByteArrayInputStream(contents);
                        }
                    };
                }
            });
        }
    }

    public static final class TestEnhancer implements JDOEnhancer {

        private static int instantiationCount;

        public TestEnhancer() {
            instantiationCount++;
        }

        private static void reset() {
            instantiationCount = 0;
        }

        @Override
        public Properties getProperties() {
            Properties properties = new Properties();
            properties.setProperty("VendorName", "test-enhancer");
            properties.setProperty("VersionNumber", "test-version");
            return properties;
        }

        @Override
        public JDOEnhancer setVerbose(boolean flag) {
            return this;
        }

        @Override
        public JDOEnhancer setOutputDirectory(String dirName) {
            return this;
        }

        @Override
        public JDOEnhancer setClassLoader(ClassLoader loader) {
            return this;
        }

        @Override
        public JDOEnhancer addPersistenceUnit(String persistenceUnit) {
            return this;
        }

        @Override
        public JDOEnhancer addClass(String className, byte[] bytes) {
            return this;
        }

        @Override
        public JDOEnhancer addClasses(String... classNames) {
            return this;
        }

        @Override
        public JDOEnhancer addFiles(String... metadataFiles) {
            return this;
        }

        @Override
        public JDOEnhancer addJar(String jarFileName) {
            return this;
        }

        @Override
        public int enhance() {
            return 0;
        }

        @Override
        public int validate() {
            return 0;
        }

        @Override
        public byte[] getEnhancedBytes(String className) {
            return new byte[0];
        }

        @Override
        public void registerMetadata(JDOMetadata metadata) {
        }

        @Override
        public JDOMetadata newMetadata() {
            return null;
        }

        @Override
        public byte[] transform(
                ClassLoader loader,
                String className,
                Class<?> classBeingRedefined,
                ProtectionDomain protectionDomain,
                byte[] classfileBuffer
        ) throws IllegalClassFormatException {
            return null;
        }
    }
}
