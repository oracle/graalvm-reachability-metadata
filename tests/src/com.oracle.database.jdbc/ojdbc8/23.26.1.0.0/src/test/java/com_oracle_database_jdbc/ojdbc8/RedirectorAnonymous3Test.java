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
public class RedirectorAnonymous3Test {
    @Test
    @Timeout(170)
    void invokesTheValueFactoryAndPreservesSqlNulls() throws Exception {
        try (Connection connection = OracleDatabase.openConnection();
                Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(30);
            try (ResultSet rows = statement.executeQuery("SELECT 42.5, CAST(NULL AS NUMBER) FROM dual")) {
                assertThat(rows.next()).isTrue();
                Amount amount = rows.getObject(1, Amount.class);
                assertThat(amount.value).isEqualByComparingTo("42.5");
                assertThat(rows.getObject(2, Amount.class)).isNull();
                assertThat(rows.wasNull()).isTrue();
                assertThat(rows.next()).isFalse();
            }
        }
    }

    public static final class Amount {
        private final BigDecimal value;

        private Amount(BigDecimal value) {
            this.value = value;
        }

        public static Amount valueOf(BigDecimal value) {
            return new Amount(value);
        }
    }
}
