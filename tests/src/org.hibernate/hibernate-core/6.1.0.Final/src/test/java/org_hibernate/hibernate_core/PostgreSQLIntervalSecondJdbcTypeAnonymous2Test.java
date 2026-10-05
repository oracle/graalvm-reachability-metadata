/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.dialect.PostgreSQLIntervalSecondJdbcType;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.java.DurationJavaType;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PGInterval;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

public class PostgreSQLIntervalSecondJdbcTypeAnonymous2Test {

    @Test
    public void extractsAPostgreSqlIntervalAsADuration() throws Exception {
        ValueExtractor<Duration> extractor = PostgreSQLIntervalSecondJdbcType.INSTANCE
                .getExtractor(DurationJavaType.INSTANCE);
        PGInterval interval = new PGInterval(0, 0, 2, 3, 4, 5.006007d);

        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:pg-interval-extractor");
                PreparedStatement statement = connection.prepareStatement("select ?")) {
            statement.setObject(1, interval);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(extractor.extract(result, 1, null))
                        .isEqualTo(Duration.ofDays(2)
                                .plusHours(3)
                                .plusMinutes(4)
                                .plusSeconds(5)
                                .plusNanos(6_007_000));
            }
        }
    }
}
