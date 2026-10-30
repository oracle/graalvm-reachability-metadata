/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_beanutils.commons_beanutils;

import org.apache.commons.beanutils.DynaBean;
import org.apache.commons.beanutils.RowSetDynaClass;
import org.junit.jupiter.api.Test;

import javax.sql.rowset.CachedRowSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JDBCDynaClassTest {
    @Test
    void derivesColumnTypesAndCopiesDisconnectedRows() throws Exception {
        RowSetDynaClass schema;
        try (CachedRowSet rows = JdbcRows.create()) {
            schema = new RowSetDynaClass(rows);
        }

        assertEquals(Integer.class, schema.getDynaProperty("id").getType());
        assertEquals(String.class, schema.getDynaProperty("label").getType());
        assertEquals(2, schema.getDynaProperties().length);
        assertEquals(1, schema.getRows().size());
        DynaBean row = (DynaBean) schema.getRows().get(0);
        assertEquals(7, row.get("id"));
        assertEquals("Ada", row.get("label"));
        row.set("label", "Grace");
        assertEquals("Grace", row.get("label"));
    }
}
