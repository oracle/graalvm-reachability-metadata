/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb_jaxb_runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Iterator;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import org.glassfish.jaxb.runtime.DatatypeConverterImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// Exercises JAXB runtime's XML datatype conversion subsystem for a service configuration workflow.
public class XmlDatatypeConfigurationTest {

    @Test
    @Timeout(60)
    void convertsConfigurationValuesBetweenXmlAndApplicationTypes() {
        DatatypeConverterImpl converter = (DatatypeConverterImpl) DatatypeConverterImpl.theInstance;
        NamespaceContext namespaces = new ConfigurationNamespaces();

        boolean enabled = converter.parseBoolean("true");
        BigDecimal retryDelay = converter.parseDecimal("1.250");
        byte[] certificateDigest = converter.parseBase64Binary("AAECAwQF");
        QName endpoint = converter.parseQName("cfg:primary", namespaces);

        assertTrue(enabled);
        assertEquals(new BigDecimal("1.250"), retryDelay);
        assertArrayEquals(new byte[] {0, 1, 2, 3, 4, 5}, certificateDigest);
        assertEquals(new QName("urn:configuration", "primary", "cfg"), endpoint);
        assertEquals("cfg:primary", converter.printQName(endpoint, namespaces));
        assertEquals("1.250", converter.printDecimal(retryDelay));
        assertEquals("AAECAwQF", converter.printBase64Binary(certificateDigest));
    }

    private static final class ConfigurationNamespaces implements NamespaceContext {

        @Override
        public String getNamespaceURI(String prefix) {
            if ("cfg".equals(prefix)) {
                return "urn:configuration";
            }
            if (prefix == null || prefix.isEmpty()) {
                return "";
            }
            return XMLConstants.NULL_NS_URI;
        }

        @Override
        public String getPrefix(String namespaceUri) {
            if ("urn:configuration".equals(namespaceUri)) {
                return "cfg";
            }
            return null;
        }

        @Override
        public Iterator<String> getPrefixes(String namespaceUri) {
            if ("urn:configuration".equals(namespaceUri)) {
                return Collections.singleton("cfg").iterator();
            }
            return Collections.emptyIterator();
        }
    }
}
