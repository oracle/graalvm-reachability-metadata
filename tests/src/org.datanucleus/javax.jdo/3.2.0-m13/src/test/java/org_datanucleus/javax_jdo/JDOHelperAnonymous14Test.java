/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_datanucleus.javax_jdo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.jdo.JDOHelper;

import org.junit.jupiter.api.Test;

public class JDOHelperAnonymous14Test {

    private static final String RESOURCE_NAME = "jdo-test-pmf.properties";

    @Test
    void loadPropertiesReadsNamedResourceFromProvidedClassLoader() {
        ResourceBackedClassLoader loader = new ResourceBackedClassLoader(
                getClass().getClassLoader(),
                RESOURCE_NAME,
                "javax.jdo.option.Name=memory-pmf\njavax.jdo.option.Multithreaded=true\n"
        );

        Map<Object, Object> properties = ExposedJDOHelper.loadProperties(loader, RESOURCE_NAME);

        assertThat(loader.lastRequestedResourceName).isEqualTo(RESOURCE_NAME);
        assertThat(properties)
                .containsEntry("javax.jdo.option.Name", "memory-pmf")
                .containsEntry("javax.jdo.option.Multithreaded", "true");
    }

    private static final class ExposedJDOHelper extends JDOHelper {
        private static Map<Object, Object> loadProperties(ClassLoader resourceLoader, String resourceName) {
            return loadPropertiesFromResource(resourceLoader, resourceName);
        }
    }

    private static final class ResourceBackedClassLoader extends ClassLoader {

        private final String resourceName;
        private final byte[] resourceContents;
        private String lastRequestedResourceName;

        private ResourceBackedClassLoader(ClassLoader parent, String resourceName, String resourceContents) {
            super(parent);
            this.resourceName = resourceName;
            this.resourceContents = resourceContents.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            lastRequestedResourceName = name;
            if (!resourceName.equals(name)) {
                return super.getResourceAsStream(name);
            }
            return new ByteArrayInputStream(resourceContents);
        }
    }
}
