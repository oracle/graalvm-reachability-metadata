/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_datanucleus.javax_jdo;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.instrument.IllegalClassFormatException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.Properties;

import javax.jdo.JDOEnhancer;
import javax.jdo.JDOHelper;
import javax.jdo.metadata.JDOMetadata;

import org.junit.jupiter.api.Test;

public class JDOHelperTest {
    private static final String JDO_ENHANCER_SERVICE_RESOURCE = "META-INF/services/" + JDOEnhancer.class.getName();

    @Test
    void loadsEnhancerFromServiceResource() throws Exception {
        Path servicesRoot = Files.createTempDirectory("jdo-enhancer-services");
        Path serviceFile = servicesRoot.resolve(JDO_ENHANCER_SERVICE_RESOURCE);
        Files.createDirectories(serviceFile.getParent());
        Files.writeString(
                serviceFile,
                TestJDOEnhancer.class.getName() + System.lineSeparator(),
                StandardCharsets.UTF_8);

        try (URLClassLoader loader = new URLClassLoader(
                new URL[] { servicesRoot.toUri().toURL() },
                getClass().getClassLoader())) {
            JDOEnhancer enhancer = JDOHelper.getEnhancer(loader);

            assertThat(enhancer).isInstanceOf(TestJDOEnhancer.class);
            assertThat(enhancer.getProperties())
                    .containsEntry("VendorName", "GraalVM reachability metadata tests")
                    .containsEntry("VersionNumber", "test");
        }
    }

    public static class TestJDOEnhancer implements JDOEnhancer {
        @Override
        public Properties getProperties() {
            Properties properties = new Properties();
            properties.setProperty("VendorName", "GraalVM reachability metadata tests");
            properties.setProperty("VersionNumber", "test");
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
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
            return null;
        }
    }
}
