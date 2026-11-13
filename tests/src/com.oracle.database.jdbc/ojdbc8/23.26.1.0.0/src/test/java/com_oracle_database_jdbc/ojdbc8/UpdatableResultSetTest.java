/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc8;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(OracleDatabase.class)
public class UpdatableResultSetTest {
    @Test
    @Timeout(170)
    void readsAndPersistsChangesThroughAnUpdatableCursor() throws Exception {
        try (Connection connection = OracleDatabase.openConnection();
                Statement setup = connection.createStatement()) {
            setup.setQueryTimeout(30);
            setup.execute("CREATE TABLE metadata_updates (id NUMBER PRIMARY KEY, label VARCHAR2(100))");
            try {
                setup.executeUpdate("INSERT INTO metadata_updates VALUES (7, 'before')");
                try (Statement statement = connection.createStatement(
                        ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE)) {
                    statement.setQueryTimeout(30);
                    try (ResultSet rows = statement.executeQuery("SELECT id, label FROM metadata_updates")) {
                        assertThat(rows.getConcurrency()).isEqualTo(ResultSet.CONCUR_UPDATABLE);
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getObject(1)).isEqualTo(new BigDecimal("7"));
                        assertThat(rows.getObject(2)).isEqualTo("before");
                        rows.updateString(2, "after");
                        assertThat(rows.getObject(2)).isEqualTo("after");
                        rows.updateRow();
                    }
                }
                try (ResultSet rows = setup.executeQuery("SELECT label FROM metadata_updates WHERE id = 7")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("after");
                    assertThat(rows.next()).isFalse();
                }
            } finally {
                setup.execute("DROP TABLE metadata_updates PURGE");
            }
        }
    }
}
