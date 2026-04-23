/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.DriverManagerDataSource;
import com.mchange.v2.c3p0.impl.C3P0ImplUtils;
import com.mchange.v2.c3p0.impl.DbAuth;
import org.junit.jupiter.api.Test;

import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

public class C3P0ImplUtilsTest {
    @Test
    void findsCredentialsAndChecksMethodSupport() throws Exception {
        DriverManagerDataSource dataSource = C3p0TestSupport.newDriverManagerDataSource("impl-utils");

        DbAuth auth = C3P0ImplUtils.findAuth(dataSource);
        assertThat(auth.getUser()).isEqualTo(C3p0TestSupport.USER);
        assertThat(auth.getPassword()).isEqualTo(C3p0TestSupport.PASSWORD);

        try (Connection connection = dataSource.getConnection()) {
            Boolean supportsPrepareStatement = C3P0ImplUtils.supportsMethod(connection, "prepareStatement", new Class[]{String.class});
            assertThat(supportsPrepareStatement).isNotNull();
        }
    }
}
