/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss.jboss_common_core;

import org.jboss.util.xml.DOMUtils;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import javax.xml.namespace.QName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DOMUtilsAnonymous1Test {
    @Test
    void createsNamespaceAwareParserAndReadsConfigurationElements() throws Exception {
        Element root = DOMUtils.parse("""
                <q:queues xmlns:q="urn:forge:queues" enabled="true" limit="12">
                    <q:queue name="orders">east</q:queue>
                </q:queues>
                """);

        assertEquals(new QName("urn:forge:queues", "queues"), DOMUtils.getElementQName(root));
        assertTrue(DOMUtils.getAttributeValueAsBoolean(root, "enabled"));
        assertEquals(Integer.valueOf(12), DOMUtils.getAttributeValueAsInteger(root, "limit"));
        Element queue = DOMUtils.getFirstChildElement(root, new QName("urn:forge:queues", "queue"));
        assertNotNull(queue);
        assertEquals("orders", DOMUtils.getAttributeValue(queue, "name"));
        assertEquals("east", DOMUtils.getTextContent(queue));
        assertSame(root, DOMUtils.getParentElement(queue));
    }
}
