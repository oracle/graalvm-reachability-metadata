/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_beanutils.commons_beanutils;

import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;
import java.sql.SQLException;
import java.sql.Types;

final class JdbcRows {
    private JdbcRows() {
    }

    static CachedRowSet create() throws SQLException {
        RowSetMetaDataImpl metadata = new RowSetMetaDataImpl();
        metadata.setColumnCount(2);
        metadata.setColumnName(1, "ID");
        metadata.setColumnType(1, Types.INTEGER);
        metadata.setColumnName(2, "LABEL");
        metadata.setColumnType(2, Types.VARCHAR);

        CachedRowSet rows = RowSetProvider.newFactory().createCachedRowSet();
        rows.setMetaData(metadata);
        rows.moveToInsertRow();
        rows.updateInt(1, 7);
        rows.updateString(2, "Ada");
        rows.insertRow();
        rows.moveToCurrentRow();
        rows.beforeFirst();
        return rows;
    }
}
