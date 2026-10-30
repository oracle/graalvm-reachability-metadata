/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import java.util.ArrayList;
import java.util.List;
import net.sf.ehcache.Element;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class ElementTest {
    @Test
    void clonesMutablePayloadAndMeasuresSerializedSize() throws Exception {
        ArrayList<String> value = new ArrayList<>(List.of("first"));
        Element original = new Element("key", value, 42);
        original.updateAccessStatistics();
        Element copy = (Element) original.clone();
        assertEquals("key", copy.getObjectKey());
        assertEquals(value, copy.getObjectValue());
        assertNotSame(value, copy.getObjectValue());
        assertEquals(42, copy.getVersion());
        assertEquals(1, copy.getHitCount());
        value.add("second");
        assertEquals(List.of("first"), copy.getObjectValue());
        assertTrue(original.getSerializedSize() > copy.getSerializedSize());
    }
}
