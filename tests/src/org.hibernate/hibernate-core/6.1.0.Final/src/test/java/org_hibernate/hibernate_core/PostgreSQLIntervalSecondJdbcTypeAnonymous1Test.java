/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.dialect.PostgreSQLIntervalSecondJdbcType;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.java.DurationJavaType;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PGInterval;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

public class PostgreSQLIntervalSecondJdbcTypeAnonymous1Test {

    @Test
    public void bindsADurationThroughAPostgreSqlInterval() throws Exception {
        ValueBinder<Duration> binder = PostgreSQLIntervalSecondJdbcType.INSTANCE
                .getBinder(DurationJavaType.INSTANCE);

        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:pg-interval");
                PreparedStatement statement = connection.prepareStatement("select ?")) {
            binder.bind(statement, Duration.ofDays(2).plusHours(3).plusMinutes(4).plusSeconds(5), 1, null);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getObject(1))
                        .isInstanceOfSatisfying(PGInterval.class, interval -> {
                            assertThat(interval.getDays()).isEqualTo(2);
                            assertThat(interval.getHours()).isEqualTo(3);
                            assertThat(interval.getMinutes()).isEqualTo(4);
                            assertThat(interval.getWholeSeconds()).isEqualTo(5);
                        });
            }
        }
    }
}
