/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_beanutils.commons_beanutils;

import org.apache.commons.beanutils.DynaBean;
import org.apache.commons.beanutils.ResultSetDynaClass;
import org.junit.jupiter.api.Test;

import javax.sql.rowset.CachedRowSet;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResultSetDynaClassTest {
    @Test
    void wrapsAndIteratesJdbcRowsWithTheirColumnTypes() throws Exception {
        try (CachedRowSet rows = JdbcRows.create()) {
            ResultSetDynaClass schema = new ResultSetDynaClass(rows, false);
            assertEquals(Integer.class, schema.getDynaProperty("ID").getType());
            assertEquals(String.class, schema.getDynaProperty("LABEL").getType());

            Iterator<?> iterator = schema.iterator();
            assertTrue(iterator.hasNext());
            DynaBean row = (DynaBean) iterator.next();
            assertEquals(7, row.get("ID"));
            assertEquals("Ada", row.get("LABEL"));
            assertFalse(iterator.hasNext());
        }
    }
}
