/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_weld.weld_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

import org.jboss.weld.resources.DefaultResourceLoader;
import org.jboss.weld.resources.spi.ResourceLoader;
import org.jboss.weld.xml.BeansXmlParser;
import org.junit.jupiter.api.Test;

public class DefaultResourceLoaderTest {
    @Test
    void loadsClassesAndSchemaWithContextClassLoader() throws Exception {
        ResourceLoader loader = DefaultResourceLoader.INSTANCE;
        assertThat(loader.classForName("org.jboss.weld.xml.BeansXmlParser"))
                .isEqualTo(BeansXmlParser.class);
        assertSchema(loader.getResource("beans_1_1.xsd"));
        Collection<URL> resources = loader.getResources("beans_1_1.xsd");
        assertThat(resources).isNotEmpty();
        for (URL resource : resources) {
            assertSchema(resource);
        }
    }

    @Test
    void fallsBackToLibraryLoaderWithoutContextClassLoader() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(null);
            ResourceLoader loader = DefaultResourceLoader.INSTANCE;
            assertThat(loader.classForName("org.jboss.weld.xml.BeansXmlParser"))
                    .isEqualTo(BeansXmlParser.class);
            assertSchema(loader.getResource("/beans_1_1.xsd"));
            Collection<URL> resources = loader.getResources("beans_1_1.xsd");
            assertThat(resources).isNotEmpty();
            for (URL resource : resources) {
                assertSchema(resource);
            }
        } finally {
            thread.setContextClassLoader(original);
        }
    }

    private static void assertSchema(URL resource) throws Exception {
        assertThat(resource).isNotNull();
        try (InputStream input = resource.openStream()) {
            assertThat(new String(input.readAllBytes(), StandardCharsets.UTF_8))
                    .contains("http://jboss.org/schema/weld/beans", "schema");
        }
    }
}
