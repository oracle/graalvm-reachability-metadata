/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.DriverManagerDataSource;
import com.mchange.v2.c3p0.util.TestUtils;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

public class TestUtilsTest {
    @Test
    void createsProxyDataSourceThatDelegatesCommonOperations() throws Exception {
        DriverManagerDataSource dataSource = C3p0TestSupport.newDriverManagerDataSource("test-utils");
        DataSource proxied = TestUtils.unreliableCommitDataSource(dataSource);

        assertThat(proxied.getLoginTimeout()).isEqualTo(0);

        SQLException lastFailure = null;
        for (int attempt = 0; attempt < 10; attempt++) {
            try (Connection connection = proxied.getConnection()) {
                assertThat(connection.getAutoCommit()).isTrue();
                return;
            } catch (SQLException ex) {
                lastFailure = ex;
            }
        }

        throw lastFailure;
    }
}
