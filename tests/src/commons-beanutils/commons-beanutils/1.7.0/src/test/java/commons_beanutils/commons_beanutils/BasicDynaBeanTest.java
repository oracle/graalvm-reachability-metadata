/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_beanutils.commons_beanutils;

import org.apache.commons.beanutils.BasicDynaBean;
import org.apache.commons.beanutils.BasicDynaClass;
import org.apache.commons.beanutils.DynaProperty;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BasicDynaBeanTest {
    @Test
    void maintainsPrimitiveIndexedAndMappedValues() {
        BasicDynaClass schema = new BasicDynaClass("entry", null, new DynaProperty[]{
                new DynaProperty("enabled", boolean.class),
                new DynaProperty("tags", String[].class),
                new DynaProperty("attributes", Map.class)
        });
        BasicDynaBean bean = new BasicDynaBean(schema);
        assertEquals(Boolean.FALSE, bean.get("enabled"));

        bean.set("enabled", Boolean.TRUE);
        bean.set("tags", new String[]{"old", "keep"});
        bean.set("tags", 0, "new");
        bean.set("attributes", new HashMap<String, String>());
        bean.set("attributes", "color", "green");

        assertEquals(Boolean.TRUE, bean.get("enabled"));
        assertEquals("new", bean.get("tags", 0));
        assertEquals("keep", bean.get("tags", 1));
        assertTrue(bean.contains("attributes", "color"));
        assertEquals("green", bean.get("attributes", "color"));
        bean.remove("attributes", "color");
        assertFalse(bean.contains("attributes", "color"));
    }
}
