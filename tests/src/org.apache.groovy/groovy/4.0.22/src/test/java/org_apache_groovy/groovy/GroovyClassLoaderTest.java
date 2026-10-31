/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovy.lang.GroovyClassLoader;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GroovyClassLoaderTest {
    @Test
    void locatesGroovySourceWithoutCompilingOrDefiningAClass() throws Exception {
        try (GroovyClassLoader loader = new GroovyClassLoader(getClass().getClassLoader())) {
            URL source = loader.getResourceLoader().loadGroovySource("examples.Inventory");
            assertNotNull(source);
            assertEquals(getClass().getResource("/examples/Inventory.groovy"), source);
            try (InputStream input = source.openStream()) {
                String content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(content.endsWith("package examples\n\nclass Inventory { String reference }\n"));
            }
        }
    }
}
