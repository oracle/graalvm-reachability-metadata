/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc8;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(OracleDatabase.class)
public class RedirectorTest {
    @Test
    @Timeout(170)
    void selectsTheSupportedValueOfConversionForARequestedType() throws Exception {
        try (Connection connection = OracleDatabase.openConnection();
                Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(30);
            try (ResultSet rows = statement.executeQuery("SELECT 'inventory-42' FROM dual")) {
                assertThat(rows.next()).isTrue();
                ItemCode code = rows.getObject(1, ItemCode.class);
                assertThat(code.value).isEqualTo("inventory-42");
                assertThat(rows.next()).isFalse();
            }
        }
    }

    public static final class ItemCode {
        private final String value;

        private ItemCode(String value) {
            this.value = value;
        }

        public static ItemCode valueOf(String value) {
            return new ItemCode(value);
        }
    }
}
