/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss.jboss_common_core;

import org.jboss.util.xml.JBossEntityResolver;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JBossEntityResolverTest {
    @Test
    void resolvesPublicIdentifierToClasspathDtdWhileParsingXml() throws Exception {
        JBossEntityResolver resolver = new JBossEntityResolver();
        resolver.registerLocalEntity("-//Forge//DTD Queue//EN", "forge-queue.dtd");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver(resolver);
        String xml = """
                <!DOCTYPE queue PUBLIC "-//Forge//DTD Queue//EN" "urn:forge:queue">
                <queue>&queueName;</queue>
                """;

        Document document = builder.parse(new InputSource(new StringReader(xml)));

        assertEquals("orders", document.getDocumentElement().getTextContent());
        assertTrue(resolver.isEntityResolved());
    }

    @Test
    void resolvesRegisteredSystemIdentifierToClasspathSchema() throws Exception {
        JBossEntityResolver resolver = new JBossEntityResolver();
        String systemId = "urn:forge:queue-schema";
        resolver.registerLocalEntity(systemId, "forge-queue.xsd");
        InputSource source = resolver.resolveEntity(null, systemId);
        assertNotNull(source);
        assertEquals(systemId, source.getSystemId());
        assertTrue(resolver.isEntityResolved());

        try (InputStream stream = source.getByteStream()) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document document = factory.newDocumentBuilder().parse(stream);
            assertEquals("schema", document.getDocumentElement().getLocalName());
            assertEquals("urn:forge:queues", document.getDocumentElement().getAttribute("targetNamespace"));
        }
    }
}
