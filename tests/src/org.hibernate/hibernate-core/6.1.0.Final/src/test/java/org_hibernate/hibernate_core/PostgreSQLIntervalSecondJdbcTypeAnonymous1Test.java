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
                PreparedStatement statement = connection.prepareStatement("select cast(? as varchar)")) {
            binder.bind(statement, Duration.ofDays(2).plusHours(3).plusMinutes(4).plusSeconds(5), 1, null);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1))
                        .contains("2 days")
                        .contains("3 hours")
                        .contains("4 mins")
                        .contains("5.0 secs");
            }
        }
    }
}
