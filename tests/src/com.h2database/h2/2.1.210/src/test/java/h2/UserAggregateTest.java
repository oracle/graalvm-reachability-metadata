/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.h2.api.AggregateFunction;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

import static org.assertj.core.api.Assertions.assertThat;

public class UserAggregateTest {
    private static final String AGGREGATE_NAME = "TEST_STRING_JOIN";
    private static final String AGGREGATE_CLASS_NAME = StringJoiningAggregate.class.getName();

    @Test
    void createsAndUsesUserDefinedAggregate() throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:user-aggregate")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE AGGREGATE " + AGGREGATE_NAME + " FOR '" + AGGREGATE_CLASS_NAME + "'");
                statement.execute("CREATE TABLE items(name VARCHAR NOT NULL)");
                statement.execute("INSERT INTO items(name) VALUES ('alpha'), ('beta'), ('gamma')");

                try (ResultSet resultSet = statement.executeQuery("SELECT " + AGGREGATE_NAME + "(name) FROM items")) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getString(1)).isEqualTo("alpha,beta,gamma");
                    assertThat(resultSet.next()).isFalse();
                }
            }
        }
    }

    public static final class StringJoiningAggregate implements AggregateFunction {
        private final StringBuilder joinedValues = new StringBuilder();

        public StringJoiningAggregate() {
        }

        @Override
        public int getType(int[] inputTypes) throws SQLException {
            assertThat(inputTypes).containsExactly(Types.VARCHAR);
            return Types.VARCHAR;
        }

        @Override
        public void add(Object value) throws SQLException {
            if (joinedValues.length() > 0) {
                joinedValues.append(',');
            }
            joinedValues.append(value);
        }

        @Override
        public Object getResult() throws SQLException {
            return joinedValues.toString();
        }
    }
}
