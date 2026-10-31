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

import org.jboss.weld.resources.ClassLoaderResourceLoader;
import org.jboss.weld.xml.BeansXmlParser;
import org.junit.jupiter.api.Test;

public class ClassLoaderResourceLoaderTest {
    @Test
    void loadsLibraryClassesAndEnumeratesSchemas() throws Exception {
        ClassLoaderResourceLoader loader = new ClassLoaderResourceLoader(getClass().getClassLoader());
        try {
            assertThat(loader.classForName("org.jboss.weld.xml.BeansXmlParser"))
                    .isEqualTo(BeansXmlParser.class);
            URL schema = loader.getResource("beans_1_1.xsd");
            assertThat(schema).isNotNull();
            try (InputStream input = schema.openStream()) {
                assertThat(new String(input.readAllBytes(), StandardCharsets.UTF_8))
                        .contains("http://jboss.org/schema/weld/beans");
            }
            Collection<URL> resources = loader.getResources("beans_1_1.xsd");
            assertThat(resources).contains(schema);
        } finally {
            loader.cleanup();
        }
    }
}
