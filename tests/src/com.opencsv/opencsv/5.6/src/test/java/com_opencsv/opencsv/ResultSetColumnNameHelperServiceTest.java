/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.opencsv.ResultSetColumnNameHelperService;
import java.sql.SQLException;
import java.sql.Types;
import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;
import org.junit.jupiter.api.Test;

public class ResultSetColumnNameHelperServiceTest {
    @Test
    void returnsConfiguredColumnHeadersInResultSetOrder() throws SQLException {
        ResultSetColumnNameHelperService service = new ResultSetColumnNameHelperService();
        service.setColumnNames(
                new String[] {"id", "name"},
                new String[] {"Identifier", "Display Name"});

        try (CachedRowSet rowSet = resultSetWithColumns("id", "name")) {
            assertThat(service.getColumnNames(rowSet))
                    .containsExactly("Identifier", "Display Name");
        }
    }

    @Test
    void rejectsMismatchedColumnAndHeaderCounts() {
        ResultSetColumnNameHelperService service = new ResultSetColumnNameHelperService();

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> service.setColumnNames(
                        new String[] {"id", "name"},
                        new String[] {"Identifier"}));
    }

    @Test
    void rejectsBlankColumnNames() {
        ResultSetColumnNameHelperService service = new ResultSetColumnNameHelperService();

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> service.setColumnNames(
                        new String[] {"id", " "},
                        new String[] {"Identifier", "Display Name"}));
    }

    @Test
    void rejectsBlankColumnHeaders() {
        ResultSetColumnNameHelperService service = new ResultSetColumnNameHelperService();

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> service.setColumnNames(
                        new String[] {"id", "name"},
                        new String[] {"Identifier", " "}));
    }

    @Test
    void rejectsConfiguredColumnMissingFromResultSet() throws SQLException {
        ResultSetColumnNameHelperService service = new ResultSetColumnNameHelperService();
        service.setColumnNames(new String[] {"missing"}, new String[] {"Missing"});

        try (CachedRowSet rowSet = resultSetWithColumns("id", "name")) {
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> service.getColumnNames(rowSet));
        }
    }

    private static CachedRowSet resultSetWithColumns(String... columnNames) throws SQLException {
        CachedRowSet rowSet = RowSetProvider.newFactory().createCachedRowSet();
        RowSetMetaDataImpl metadata = new RowSetMetaDataImpl();
        metadata.setColumnCount(columnNames.length);
        for (int columnIndex = 0; columnIndex < columnNames.length; columnIndex++) {
            int jdbcColumnIndex = columnIndex + 1;
            metadata.setColumnName(jdbcColumnIndex, columnNames[columnIndex]);
            metadata.setColumnLabel(jdbcColumnIndex, columnNames[columnIndex]);
            metadata.setColumnType(jdbcColumnIndex, Types.VARCHAR);
        }
        rowSet.setMetaData(metadata);
        return rowSet;
    }
}
