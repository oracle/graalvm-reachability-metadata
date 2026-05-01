/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.junit.jupiter.api.Test;

import java.sql.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

public class ValueToObjectConverterTest {
    @Test
    void convertsSqlArrayToRequestedJavaArrayType() throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:valueToObjectConverter;DB_CLOSE_DELAY=-1")) {
            Array array = connection.createArrayOf("VARCHAR", new String[] { "alpha", "beta", "gamma" });
            try (PreparedStatement statement = connection.prepareStatement("SELECT ? AS labels")) {
                statement.setArray(1, array);
                try (ResultSet resultSet = statement.executeQuery()) {
                    assertThat(resultSet.next()).isTrue();

                    String[] labels = resultSet.getObject("labels", String[].class);

                    assertThat(labels).containsExactly("alpha", "beta", "gamma");
                    assertThat(resultSet.next()).isFalse();
                }
            } finally {
                array.free();
            }
        }
    }
}
