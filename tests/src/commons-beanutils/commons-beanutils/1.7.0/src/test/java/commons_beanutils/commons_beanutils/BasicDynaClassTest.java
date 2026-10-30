/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_beanutils.commons_beanutils;

import org.apache.commons.beanutils.BasicDynaClass;
import org.apache.commons.beanutils.DynaBean;
import org.apache.commons.beanutils.DynaProperty;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class BasicDynaClassTest {
    @Test
    void createsIndependentBeansFromAPropertySchema() throws Exception {
        BasicDynaClass schema = new BasicDynaClass("person", null, new DynaProperty[]{
                new DynaProperty("name", String.class),
                new DynaProperty("visits", int.class)
        });
        DynaBean first = schema.newInstance();
        DynaBean second = schema.newInstance();
        first.set("name", "Ada");
        first.set("visits", 3);

        assertSame(schema, first.getDynaClass());
        assertEquals("person", schema.getName());
        assertEquals("Ada", first.get("name"));
        assertEquals(3, first.get("visits"));
        assertNull(second.get("name"));
        assertEquals(0, second.get("visits"));
    }
}
