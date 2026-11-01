/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.xml.PluggableSchemaResolver;
import org.xml.sax.InputSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PluggableSchemaResolverTest {
    @Test
    void resolvesSchemaUrlToPackagedSchemaBytes() throws Exception {
        PluggableSchemaResolver resolver = new PluggableSchemaResolver(getClass().getClassLoader());
        String systemId = "http://www.springframework.org/schema/beans/spring-beans.xsd";
        InputSource source = resolver.resolveEntity(null, systemId);
        assertNotNull(source);
        assertEquals(systemId, source.getSystemId());
        try (InputStream stream = source.getByteStream()) {
            String schema = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(schema.contains("targetNamespace=\"http://www.springframework.org/schema/beans\""));
            assertTrue(schema.contains("name=\"bean\""));
        }
    }
}
