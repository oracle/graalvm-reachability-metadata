/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_jtds.jtds;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class SharedSocketTest {
    @Test
    void connectsWithDefaultAndExplicitLocalSocketBinding(@TempDir Path directory) throws Exception {
        try (SqlServerDatabase database = SqlServerDatabase.start(directory)) {
            for (String bindAddress : new String[]{"", "127.0.0.1"}) {
                try (Connection connection = database.openConnection(bindAddress);
                     Statement statement = connection.createStatement()) {
                    statement.setQueryTimeout(15);
                    try (ResultSet result = statement.executeQuery("SELECT 42 AS answer, DB_NAME() AS catalog")) {
                        assertThat(result.next()).isTrue();
                        assertThat(result.getInt("answer")).isEqualTo(42);
                        assertThat(result.getString("catalog")).isEqualTo("master");
                        assertThat(result.next()).isFalse();
                    }
                }
            }
        }
    }
}
