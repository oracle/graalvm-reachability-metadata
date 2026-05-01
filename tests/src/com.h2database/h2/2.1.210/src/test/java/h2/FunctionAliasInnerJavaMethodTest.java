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
import java.sql.Statement;
import java.util.StringJoiner;

import static org.assertj.core.api.Assertions.assertThat;

public class FunctionAliasInnerJavaMethodTest {
    @Test
    void invokesVarArgsFunctionAlias() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:function_alias_inner_java_method");
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE ALIAS JOIN_NUMBERS FOR 'h2.FunctionAliasInnerJavaMethodTest.joinNumbers'
                    """);

            try (ResultSet resultSet = statement.executeQuery("SELECT JOIN_NUMBERS('numbers', 4, 8, 15, 16)")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString(1)).isEqualTo("numbers:4,8,15,16");
                assertThat(resultSet.next()).isFalse();
            }
        }
    }

    public static String joinNumbers(String label, int... values) {
        StringJoiner joiner = new StringJoiner(",", label + ":", "");
        for (int value : values) {
            joiner.add(Integer.toString(value));
        }
        return joiner.toString();
    }
}
