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
    void invokesVarArgsFunctionAliasThroughSql() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:functionAliasInnerJavaMethod");
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE ALIAS JOIN_WITH_PREFIX FOR 'h2.FunctionAliasInnerJavaMethodTest.joinWithPrefix'");

            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT JOIN_WITH_PREFIX('letters', 'alpha', 'beta', 'gamma')")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString(1)).isEqualTo("letters:alpha,beta,gamma");
                assertThat(resultSet.next()).isFalse();
            }
        }
    }

    public static String joinWithPrefix(String prefix, String... values) {
        StringJoiner joiner = new StringJoiner(",");
        for (String value : values) {
            joiner.add(value);
        }
        return prefix + ":" + joiner;
    }
}
