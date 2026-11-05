/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ojdbc8;

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
    @Timeout(120)
    void readsAndUpdatesValuesUsingTheirDeclaredColumnTypes() throws Exception {
        try (Connection connection = OracleDatabase.openConnection();
                Statement setup = connection.createStatement()) {
            setup.execute("create table forge_contacts (id number, label varchar2(40))");
            try {
                setup.executeUpdate("insert into forge_contacts values (7, 'original')");
                try (Statement statement = connection.createStatement(
                        ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
                        ResultSet rows = statement.executeQuery("select id, label from forge_contacts")) {
                    assertThat(rows.getConcurrency()).isEqualTo(ResultSet.CONCUR_UPDATABLE);
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getObject(1)).isEqualTo(new BigDecimal("7"));
                    assertThat(rows.getObject(2)).isEqualTo("original");
                    rows.updateString(2, "updated");
                    assertThat(rows.getObject(2)).isEqualTo("updated");
                    rows.updateRow();
                }
                try (ResultSet stored = setup.executeQuery("select label from forge_contacts where id = 7")) {
                    assertThat(stored.next()).isTrue();
                    assertThat(stored.getString(1)).isEqualTo("updated");
                    assertThat(stored.next()).isFalse();
                }
            } finally {
                setup.execute("drop table forge_contacts purge");
            }
        }
    }
}
