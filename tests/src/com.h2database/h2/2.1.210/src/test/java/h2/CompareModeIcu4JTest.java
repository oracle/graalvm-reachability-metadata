/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class CompareModeIcu4JTest {
    @Test
    void configuresIcu4jCollatorsForSupportedLocaleNameForms() throws SQLException {
        assertIcu4jCollationCanBeInstalled("ICU4J_EN", "PRIMARY");
        assertIcu4jCollationCanBeInstalled("ICU4J_EN_US", "SECONDARY");
        assertIcu4jCollationCanBeInstalled("ICU4J_ENGLISH", "TERTIARY");
    }

    private static void assertIcu4jCollationCanBeInstalled(String collation, String strength) throws SQLException {
        String databaseName = "icu4j" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:" + databaseName);
                Statement statement = connection.createStatement()) {
            statement.execute("SET COLLATION " + collation + " STRENGTH " + strength);

            assertCollationSetting(statement, collation, strength);

            statement.execute("CREATE TABLE words(word_value VARCHAR)");
            statement.execute("INSERT INTO words(word_value) VALUES ('Zulu'), ('alpha'), ('Echo')");
            try (ResultSet resultSet = statement.executeQuery("SELECT word_value FROM words ORDER BY word_value")) {
                assertThat(readValues(resultSet)).containsExactlyInAnyOrder("Zulu", "alpha", "Echo");
            }
        }
    }

    private static void assertCollationSetting(Statement statement, String collation, String strength) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery("""
                SELECT SETTING_VALUE
                FROM INFORMATION_SCHEMA.SETTINGS
                WHERE SETTING_NAME = 'COLLATION'
                """)) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString(1)).isEqualTo(collation + " STRENGTH " + strength);
            assertThat(resultSet.next()).isFalse();
        }
    }

    private static List<String> readValues(ResultSet resultSet) throws SQLException {
        List<String> values = new ArrayList<>();
        while (resultSet.next()) {
            values.add(resultSet.getString(1));
        }
        return values;
    }
}
