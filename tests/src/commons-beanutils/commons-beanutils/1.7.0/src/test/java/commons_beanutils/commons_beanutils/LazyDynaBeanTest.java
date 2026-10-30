/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_beanutils.commons_beanutils;

import org.apache.commons.beanutils.DynaProperty;
import org.apache.commons.beanutils.LazyDynaBean;
import org.apache.commons.beanutils.LazyDynaClass;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class LazyDynaBeanTest {
    @Test
    void createsAndGrowsTypedIndexedProperties() {
        LazyDynaClass schema = new LazyDynaClass("indexed", new DynaProperty[]{
                new DynaProperty("numbers", int[].class),
                new DynaProperty("items", ArrayList.class)
        });
        LazyDynaBean bean = new LazyDynaBean(schema);

        bean.set("numbers", 2, 17);
        bean.set("numbers", 4, 29);
        bean.set("items", 2, "last");

        assertArrayEquals(new int[]{0, 0, 17, 0, 29}, (int[]) bean.get("numbers"));
        assertInstanceOf(ArrayList.class, bean.get("items"));
        assertNull(bean.get("items", 0));
        assertEquals("last", bean.get("items", 2));
    }

    @Test
    void createsTypedMappedNestedAndOrdinaryProperties() {
        LazyDynaClass schema = new LazyDynaClass("record", new DynaProperty[]{
                new DynaProperty("attributes", HashMap.class),
                new DynaProperty("details", LazyDynaBean.class),
                new DynaProperty("owner", SampleBean.class)
        });
        LazyDynaBean bean = new LazyDynaBean(schema);

        bean.set("attributes", "color", "blue");
        LazyDynaBean details = (LazyDynaBean) bean.get("details");
        details.set("active", Boolean.TRUE);
        SampleBean owner = (SampleBean) bean.get("owner");
        owner.setName("Ada");

        assertInstanceOf(HashMap.class, bean.get("attributes"));
        assertEquals("blue", bean.get("attributes", "color"));
        assertEquals(Boolean.TRUE, details.get("active"));
        assertSame(details, bean.get("details"));
        assertEquals("Ada", ((SampleBean) bean.get("owner")).getName());
    }
}
