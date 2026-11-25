/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc8;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import oracle.jdbc.internal.OracleConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(OracleDatabase.class)
public class DirectPathBufferMarshalerTest {
    @Test
    @Timeout(170)
    void loadsRowsSpanningMultipleDirectPathBufferPieces() throws Exception {
        String[] columns = IntStream.range(0, 64).mapToObj(index -> "C" + index).toArray(String[]::new);
        String definitions = IntStream.range(0, columns.length)
                .mapToObj(index -> columns[index] + " VARCHAR2(4000)")
                .collect(Collectors.joining(", "));
        String value = "direct-path-" + "x".repeat(2988);
        try (Connection connection = OracleDatabase.openConnection();
                Statement setup = connection.createStatement()) {
            setup.setQueryTimeout(30);
            setup.execute("CREATE TABLE metadata_direct_path (" + definitions + ")");
            try {
                connection.setAutoCommit(false);
                OracleConnection oracle = connection.unwrap(OracleConnection.class);
                try (PreparedStatement insert = oracle.prepareDirectPath(null, "METADATA_DIRECT_PATH", columns)) {
                    insert.setQueryTimeout(30);
                    for (int row = 0; row < 2; row++) {
                        for (int column = 1; column <= columns.length; column++) {
                            insert.setString(column, value);
                        }
                        insert.addBatch();
                    }
                    assertThat(insert.executeBatch()).hasSize(2);
                    connection.commit();
                }
                try (ResultSet rows = setup.executeQuery("SELECT * FROM metadata_direct_path")) {
                    for (int row = 0; row < 2; row++) {
                        assertThat(rows.next()).isTrue();
                        for (int column = 1; column <= columns.length; column++) {
                            assertThat(rows.getString(column)).isEqualTo(value);
                        }
                    }
                    assertThat(rows.next()).isFalse();
                }
            } finally {
                connection.rollback();
                setup.execute("DROP TABLE metadata_direct_path PURGE");
            }
        }
    }
}
