/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_beanutils.commons_beanutils;

import org.apache.commons.beanutils.DynaBean;
import org.apache.commons.beanutils.WrapDynaBean;
import org.apache.commons.beanutils.WrapDynaClass;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

public class WrapDynaClassTest {
    @Test
    void createsAndUpdatesWrappedJavaBeans() throws Exception {
        WrapDynaClass schema = WrapDynaClass.createDynaClass(SampleBean.class);
        DynaBean first = schema.newInstance();
        DynaBean second = schema.newInstance();
        first.set("name", "Ada");
        first.set("count", 4);
        first.set("tags", 0, "primary");

        SampleBean wrapped = (SampleBean) ((WrapDynaBean) first).getInstance();
        assertEquals("Ada", wrapped.getName());
        assertEquals(4, wrapped.getCount());
        assertEquals("primary", first.get("tags", 0));
        assertEquals("untitled", second.get("name"));
        assertEquals(String.class, schema.getDynaProperty("name").getType());
        assertNotSame(((WrapDynaBean) first).getInstance(), ((WrapDynaBean) second).getInstance());
        assertSame(schema, WrapDynaClass.createDynaClass(SampleBean.class));
    }
}
