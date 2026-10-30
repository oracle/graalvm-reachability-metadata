/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_sql;

import groovy.lang.Closure;
import groovy.sql.GroovyResultSet;
import groovy.sql.Sql;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class GroovyResultSetProxyTest {
    @Test
    void eachRowExposesJdbcAndGroovyColumnAccess() throws Exception {
        try (Sql sql = Sql.newInstance("jdbc:h2:mem:result_set_proxy", "sa", "", "org.h2.Driver")) {
            sql.execute("""
                    CREATE TABLE inventory (
                        id INTEGER PRIMARY KEY,
                        item_name VARCHAR(80) NOT NULL,
                        quantity INTEGER NOT NULL
                    )
                    """);
            sql.executeUpdate("INSERT INTO inventory VALUES (?, ?, ?)", List.of(1, "notebook", 3));
            sql.executeUpdate("INSERT INTO inventory VALUES (?, ?, ?)", List.of(2, "pencil", 7));

            List<Integer> ids = new ArrayList<>();
            List<String> names = new ArrayList<>();
            List<Object> quantities = new ArrayList<>();
            sql.eachRow("SELECT id, item_name, quantity FROM inventory ORDER BY id", new Closure<Void>(this) {
                public Void doCall(GroovyResultSet row) throws SQLException {
                    int id = row.getInt("ID");
                    assertEquals(id, row.getAt(0));
                    assertEquals(row.getProperty("ITEM_NAME"), row.getAt(1));
                    assertEquals(row.getAt("QUANTITY"), row.getAt(-1));
                    ids.add(id);
                    names.add(row.getString("ITEM_NAME"));
                    quantities.add(row.getAt(-1));
                    return null;
                }
            });

            assertEquals(List.of(1, 2), ids);
            assertEquals(List.of("notebook", "pencil"), names);
            assertEquals(List.of(3, 7), quantities);
        }
    }
}
