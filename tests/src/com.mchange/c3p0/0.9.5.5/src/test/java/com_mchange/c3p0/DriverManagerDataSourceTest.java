/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.DriverManagerDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;

public class DriverManagerDataSourceTest {
    @Test
    void connectsUsingTheNamedDriverAndExplicitCredentials() throws Exception {
        DriverManagerDataSource source = C3p0Support.unpooled();
        source.setForceUseNamedDriverClass(true);
        try (Connection connection = source.getConnection()) {
            C3p0Support.assertQuery(connection, 41);
        }
        try (Connection connection = source.getConnection("sa", "")) {
            C3p0Support.assertQuery(connection, 42);
        }
    }
}
