/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.test.FreezableDriverManagerDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

public class FreezableDriverManagerDataSourceTest {
    @Test
    void loadsDriverBeforeAcquiringConnection() throws Exception {
        FreezableDriverManagerDataSource dataSource = new FreezableDriverManagerDataSource();
        dataSource.setDriverClass(C3p0TestSupport.H2_DRIVER);
        dataSource.setJdbcUrl(C3p0TestSupport.jdbcUrl("freezable"));
        dataSource.setUser(C3p0TestSupport.USER);
        dataSource.setPassword(C3p0TestSupport.PASSWORD);

        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.isValid(1)).isTrue();
        }
    }
}
