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
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

public class ValueToObjectConverterTest {
    @Test
    public void convertsSqlArrayToTypedJavaArray() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:value-to-object-converter")) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT ?")) {
                statement.setArray(1, connection.createArrayOf("VARCHAR", new String[] {"alpha", "beta", "gamma"}));

                try (ResultSet resultSet = statement.executeQuery()) {
                    assertThat(resultSet.next()).isTrue();

                    String[] values = resultSet.getObject(1, String[].class);

                    assertThat(values).containsExactly("alpha", "beta", "gamma");
                    assertThat(values.getClass().getComponentType()).isEqualTo(String.class);
                    assertThat(resultSet.next()).isFalse();
                }
            }
        }
    }
}
