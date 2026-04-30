/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mchange.v2.c3p0.test.FreezableDriverManagerDataSource;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

public class FreezableDriverManagerDataSourceTest {
    @Test
    void loadsConfiguredDriverClassBeforeLookingUpDriver() {
        FreezableDriverManagerDataSource dataSource = new FreezableDriverManagerDataSource(false);
        dataSource.setDriverClass(String.class.getName());
        dataSource.setJdbcUrl("jdbc:c3p0-test:freezable-driver-manager");

        assertThatThrownBy(dataSource::getConnection)
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("No suitable driver");
    }
}
