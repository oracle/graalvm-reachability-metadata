/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_jdbc;

import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

public class DriverManagerDataSourceTest {
    @Test
    void loadsConfiguredDriverAndOpensIndependentConnections() throws Exception {
        DriverManagerDataSource source = new DriverManagerDataSource();
        source.setDriverClassName("org.h2.Driver");
        source.setUrl("jdbc:h2:mem:" + UUID.randomUUID());
        source.setUsername("sa");
        source.setPassword("");

        try (Connection first = source.getConnection();
                Connection second = source.getConnection("sa", "");
                Statement statement = first.createStatement()) {
            assertThat(second).isNotSameAs(first);
            statement.executeUpdate("CREATE TABLE orders (id INTEGER PRIMARY KEY)");
            assertThat(statement.executeUpdate("INSERT INTO orders VALUES (17)")).isEqualTo(1);
            assertThat(JdbcTestSupport.queryInt(second, "SELECT id FROM orders")).isEqualTo(17);
        }
    }
}
