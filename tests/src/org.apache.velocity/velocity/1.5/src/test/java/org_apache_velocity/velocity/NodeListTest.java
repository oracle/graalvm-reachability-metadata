/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import org.apache.velocity.anakia.NodeList;
import org.jdom.Element;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

public class NodeListTest {
    @Test
    void clonesNodeCollectionWithoutDuplicatingXmlElements() throws Exception {
        Element customer = new Element("customer").setText("Atlas");
        NodeList original = new NodeList(customer);
        NodeList copy = (NodeList) original.clone();
        assertNotSame(original.getList(), copy.getList());
        assertSame(customer, copy.get(0));
        assertEquals(original, copy);

        copy.add(new Element("customer").setText("Orion"));
        assertEquals(1, original.size());
        assertEquals(2, copy.size());
        assertEquals("<customer>Atlas</customer><customer>Orion</customer>", copy.toString());
    }
}
