/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ojdbc8;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;
import oracle.jdbc.internal.OracleConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(OracleDatabase.class)
public class DirectPathBufferMarshalerTest {
    @Test
    @Timeout(120)
    void loadsABatchLargerThanTheInitialMarshallingBuffers() throws Exception {
        try (Connection connection = OracleDatabase.openConnection();
                Statement setup = connection.createStatement()) {
            setup.execute("create table forge_direct_load (id number, payload varchar2(200))");
            try {
                connection.setAutoCommit(false);
                OracleConnection oracle = connection.unwrap(OracleConnection.class);
                try (PreparedStatement load = oracle.prepareDirectPath(
                        OracleDatabase.USER.toUpperCase(Locale.ROOT), "FORGE_DIRECT_LOAD", new String[] {"ID", "PAYLOAD"})) {
                    for (int row = 0; row < 400; row++) {
                        load.setInt(1, row);
                        load.setString(2, "payload-" + row + "-" + "x".repeat(120));
                        load.addBatch();
                    }
                    load.executeBatch();
                    connection.commit();
                }
                try (ResultSet rows = setup.executeQuery("select count(*), sum(id) from forge_direct_load")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isEqualTo(400);
                    assertThat(rows.getInt(2)).isEqualTo(79800);
                    assertThat(rows.next()).isFalse();
                }
            } finally {
                setup.execute("drop table forge_direct_load purge");
            }
        }
    }
}
