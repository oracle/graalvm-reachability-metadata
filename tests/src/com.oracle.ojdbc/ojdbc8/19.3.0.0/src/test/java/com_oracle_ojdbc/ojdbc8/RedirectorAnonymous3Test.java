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
public class RedirectorAnonymous3Test {
    @Test
    @Timeout(120)
    void invokesTheNumericValueFactoryForEachResultRow() throws Exception {
        try (Connection connection = OracleDatabase.openConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("select 19.75 from dual union all select 28.50 from dual")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getObject(1, Amount.class).value).isEqualByComparingTo("19.75");
            assertThat(rows.next()).isTrue();
            assertThat(rows.getObject(1, Amount.class).value).isEqualByComparingTo("28.50");
            assertThat(rows.next()).isFalse();
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
