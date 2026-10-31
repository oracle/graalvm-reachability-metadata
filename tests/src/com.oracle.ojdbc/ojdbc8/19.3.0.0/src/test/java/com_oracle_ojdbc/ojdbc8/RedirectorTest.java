/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ojdbc8;

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
    @Timeout(120)
    void convertsCharacterColumnsThroughAnApplicationValueFactory() throws Exception {
        try (Connection connection = OracleDatabase.openConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("select 'delivery-ready', cast(null as varchar2(30)) from dual")) {
            assertThat(rows.next()).isTrue();
            Label label = rows.getObject(1, Label.class);
            assertThat(label.text).isEqualTo("delivery-ready");
            assertThat(rows.wasNull()).isFalse();
            assertThat(rows.getObject(2, Label.class)).isNull();
            assertThat(rows.wasNull()).isTrue();
            assertThat(rows.next()).isFalse();
        }
    }

    public static final class Label {
        private final String text;

        private Label(String text) {
            this.text = text;
        }

        public static Label valueOf(String text) {
            return new Label(text);
        }
    }
}
