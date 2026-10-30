/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_beanutils.commons_beanutils;

import org.apache.commons.beanutils.DynaBean;
import org.apache.commons.beanutils.LazyDynaClass;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LazyDynaClassTest {
    @Test
    void addsAndRemovesPropertiesAndRestrictsSchemaChanges() throws Exception {
        LazyDynaClass schema = new LazyDynaClass("editable");
        schema.add("name", String.class);
        DynaBean bean = schema.newInstance();
        bean.set("name", "Ada");
        bean.set("count", 5);

        assertEquals("Ada", bean.get("name"));
        assertTrue(schema.isDynaProperty("count"));
        assertEquals(Integer.class, schema.getDynaProperty("count").getType());
        schema.remove("count");
        assertFalse(schema.isDynaProperty("count"));
        schema.setReturnNull(true);
        assertNull(schema.getDynaProperty("missing"));
        schema.setRestricted(true);
        assertThrows(IllegalStateException.class, () -> schema.add("extra", String.class));
        assertEquals("Ada", bean.get("name"));
    }
}
