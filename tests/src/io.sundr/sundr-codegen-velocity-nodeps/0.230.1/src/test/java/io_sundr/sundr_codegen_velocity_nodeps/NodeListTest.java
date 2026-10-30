/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.sundr.deps.org.apache.velocity.anakia.NodeList;
import org.jdom.Element;
import org.junit.jupiter.api.Test;

public class NodeListTest {
    @Test
    void exercisesPublicApi() throws Exception {
        NodeList original = new NodeList(new Element("item").setText("value"));
        NodeList copy = (NodeList) original.clone();
        assertNotSame(original, copy);
        assertEquals(original, copy);
        copy.clear();
        assertEquals(1, original.size());
        assertEquals(0, copy.size());
        assertTrue(original.toString().contains("<item>value</item>"));
    }
}
