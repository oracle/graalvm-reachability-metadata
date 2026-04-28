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
import java.sql.Statement;
import java.sql.Types;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class UserAggregateTest {
    private static final String URL = "jdbc:h2:mem:userAggregate";

    @Test
    void createsAndInvokesUserAggregateFromConfiguredClassName() throws Exception {
        SumAggregate.reset();

        try (Connection connection = DriverManager.getConnection(URL);
                Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
            statement.execute("CREATE TABLE scores(name VARCHAR, score INT)");
            statement.execute("INSERT INTO scores VALUES ('alpha', 7), ('bravo', 11), ('charlie', NULL)");
            statement.execute("""
                    CREATE AGGREGATE SUM_NONNULL FOR '%s'
                    """.formatted(SumAggregate.class.getName()));

            try (ResultSet resultSet = statement.executeQuery("SELECT SUM_NONNULL(score) FROM scores")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getLong(1)).isEqualTo(18L);
                assertThat(resultSet.next()).isFalse();
            }
        }

        assertThat(SumAggregate.constructorCount.get()).isGreaterThan(0);
        assertThat(SumAggregate.addCount.get()).isEqualTo(3);
    }

    public static class SumAggregate implements AggregateFunction {
        static final AtomicInteger constructorCount = new AtomicInteger();
        static final AtomicInteger addCount = new AtomicInteger();
        private long sum;

        public SumAggregate() {
            constructorCount.incrementAndGet();
        }

        static void reset() {
            constructorCount.set(0);
            addCount.set(0);
        }

        @Override
        public int getType(int[] inputTypes) {
            assertThat(inputTypes).containsExactly(Types.INTEGER);
            return Types.BIGINT;
        }

        @Override
        public void add(Object value) {
            addCount.incrementAndGet();
            if (value instanceof Number number) {
                sum += number.longValue();
            }
        }

        @Override
        public Object getResult() {
            return sum;
        }
    }
}
