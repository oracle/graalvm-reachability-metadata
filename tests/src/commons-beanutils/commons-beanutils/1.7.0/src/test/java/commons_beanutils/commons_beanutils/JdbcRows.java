/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_beanutils.commons_beanutils;

import org.h2.tools.SimpleResultSet;

import java.sql.ResultSet;
import java.sql.Types;

final class JdbcRows {
    private JdbcRows() {
    }

    static ResultSet create() {
        SimpleResultSet rows = new SimpleResultSet();
        rows.addColumn("ID", Types.INTEGER, 10, 0);
        rows.addColumn("LABEL", Types.VARCHAR, 40, 0);
        rows.addRow(7, "Ada");
        return rows;
    }
}
