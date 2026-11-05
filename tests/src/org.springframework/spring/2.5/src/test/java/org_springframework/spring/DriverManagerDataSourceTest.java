/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import java.sql.Connection;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DriverManagerDataSourceTest {
    @Test
    void loadsConfiguredDriverAndConnects() throws Exception {
        DriverManagerDataSource source = new DriverManagerDataSource();
        source.setDriverClassName("org.h2.Driver");
        source.setUrl("jdbc:h2:mem:springdriver");
        source.setUsername("sa");
        source.setPassword("");
        try (Connection connection = source.getConnection()) {
            assertEquals(42, TestDatabase.query(connection));
        }
    }
}
