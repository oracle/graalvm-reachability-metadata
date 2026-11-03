/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tika.tika_core;

import org.apache.tika.utils.XMLReaderUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.w3c.dom.Document;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Tika's secure XML document parsing subsystem for an application import pipeline.
public class SecureXmlDocumentParsingTest {
    @Test
    @Timeout(60)
    public void readsAnImportDocumentAndRejectsExternalEntities() throws Exception {
        String importDocument = """
                <import>
                  <customer id="ACME-42">Acme Corporation</customer>
                  <total currency="USD">19.95</total>
                </import>
                """;

        Document document = XMLReaderUtils.buildDOM(
                new ByteArrayInputStream(importDocument.getBytes(StandardCharsets.UTF_8)));

        assertEquals("import", document.getDocumentElement().getTagName());
        assertEquals("Acme Corporation",
                document.getElementsByTagName("customer").item(0).getTextContent().strip());
        assertEquals("ACME-42",
                document.getElementsByTagName("customer").item(0).getAttributes()
                        .getNamedItem("id").getNodeValue());
        assertEquals("USD",
                document.getElementsByTagName("total").item(0).getAttributes()
                        .getNamedItem("currency").getNodeValue());

        String externalEntityDocument = """
                <!DOCTYPE import [
                  <!ENTITY customer SYSTEM "file:///etc/passwd">
                ]>
                <import><customer>&customer;</customer></import>
                """;
        Document safeDocument = XMLReaderUtils.buildDOM(
                new ByteArrayInputStream(externalEntityDocument.getBytes(StandardCharsets.UTF_8)));
        assertEquals("", safeDocument.getElementsByTagName("customer").item(0).getTextContent());
    }
}
