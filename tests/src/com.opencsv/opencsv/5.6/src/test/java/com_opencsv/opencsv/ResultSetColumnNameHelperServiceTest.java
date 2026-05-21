/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.ResultSetColumnNameHelperService;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Types;
import java.util.Locale;
import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ResultSetColumnNameHelperServiceTest {
    @Test
    void reportsInvalidConfiguredColumnAndHeaderNames() {
        ResultSetColumnNameHelperService helper = helperWithUsLocale();

        assertThatThrownBy(() -> helper.setColumnNames(new String[] {"id"}, new String[] {"Identifier", "Name"}))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("number of column names");
        assertThatThrownBy(() -> helper.setColumnNames(new String[] {"id", " "}, new String[] {"Identifier", "Name"}))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Column names cannot be null");
        assertThatThrownBy(() -> helper.setColumnNames(new String[] {"id", "name"}, new String[] {"Identifier", " "}))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Column header names cannot be null");
    }

    @Test
    void reportsConfiguredColumnThatIsAbsentFromResultSet() throws SQLException {
        ResultSetColumnNameHelperService helper = helperWithUsLocale();
        helper.setColumnNames(new String[] {"missing"}, new String[] {"Missing"});

        assertThatThrownBy(() -> helper.getColumnNames(resultSetWithColumns("id")))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("missing")
                .hasMessageContaining("does not exist");
    }

    private static ResultSetColumnNameHelperService helperWithUsLocale() {
        ResultSetColumnNameHelperService helper = new ResultSetColumnNameHelperService();
        helper.setErrorLocale(Locale.US);
        return helper;
    }

    private static CachedRowSet resultSetWithColumns(String... columnLabels) throws SQLException {
        CachedRowSet rowSet = RowSetProvider.newFactory().createCachedRowSet();
        RowSetMetaDataImpl metadata = new RowSetMetaDataImpl();
        metadata.setColumnCount(columnLabels.length);
        for (int index = 1; index <= columnLabels.length; index++) {
            String columnLabel = columnLabels[index - 1];
            metadata.setColumnLabel(index, columnLabel);
            metadata.setColumnName(index, columnLabel);
            metadata.setColumnType(index, Types.VARCHAR);
        }
        rowSet.setMetaData(metadata);
        return rowSet;
    }
}
