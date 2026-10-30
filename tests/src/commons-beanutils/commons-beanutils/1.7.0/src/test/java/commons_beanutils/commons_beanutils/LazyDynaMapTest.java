/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_beanutils.commons_beanutils;

import org.apache.commons.beanutils.DynaProperty;
import org.apache.commons.beanutils.LazyDynaMap;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LazyDynaMapTest {
    @Test
    void exposesAndUpdatesTheBackingMapWithLazyCollections() {
        Map<String, Object> values = new HashMap<>();
        values.put("name", "Ada");
        LazyDynaMap bean = new LazyDynaMap("record", values);

        bean.set("name", "Grace");
        bean.set("tags", 2, "last");
        bean.set("attributes", "color", "blue");

        assertEquals("Grace", values.get("name"));
        assertSame(values.get("tags"), bean.get("tags"));
        assertNull(bean.get("tags", 0));
        assertEquals("last", bean.get("tags", 2));
        assertEquals("blue", bean.get("attributes", "color"));
        assertEquals("record", bean.getName());
        assertEquals(String.class, bean.getDynaProperty("name").getType());

        values.put("name", "Lin");
        assertEquals("Lin", bean.get("name"));
        bean.setRestricted(true);
        bean.set("name", "Ada");
        assertEquals("Ada", values.get("name"));
        assertThrows(IllegalArgumentException.class, () -> bean.set("extra", "value"));
    }

    @Test
    void initializesTypedPropertiesWhenCreatingAnIndependentInstance() {
        LazyDynaMap schema = new LazyDynaMap("typed", new DynaProperty[]{
                new DynaProperty("owner", SampleBean.class),
                new DynaProperty("items", ArrayList.class),
                new DynaProperty("attributes", HashMap.class)
        });
        LazyDynaMap copy = (LazyDynaMap) schema.newInstance();
        SampleBean owner = (SampleBean) copy.get("owner");
        owner.setName("Grace");
        copy.set("items", 1, "second");
        copy.set("attributes", "mode", "fast");

        assertEquals("Grace", ((SampleBean) copy.get("owner")).getName());
        assertEquals("second", copy.get("items", 1));
        assertEquals("fast", copy.get("attributes", "mode"));
        assertEquals("untitled", ((SampleBean) schema.get("owner")).getName());
        assertEquals(0, ((ArrayList<?>) schema.get("items")).size());
        assertEquals(0, ((Map<?, ?>) schema.get("attributes")).size());
    }
}
