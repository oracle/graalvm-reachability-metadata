/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package flyway;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.flywaydb.core.internal.exception.FlywaySqlException;
import org.flywaydb.core.internal.exception.sqlExceptions.FlywaySqlNoIntegratedAuthException;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FlywaySqlExceptionTest {

    @Test
    void throwsSpecificExceptionForIntegratedAuthenticationDriverError() {
        SQLException sqlException = new SQLException(
                "This driver is not configured for integrated authentication",
                "08S01");
        DataSource dataSource = getDataSource();

        assertThatThrownBy(() -> FlywaySqlException.throwFlywayExceptionIfPossible(sqlException, dataSource))
                .isInstanceOf(FlywaySqlNoIntegratedAuthException.class)
                .hasMessageContaining("integrated authentication");
    }

    private DataSource getDataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setUrl("jdbc:h2:mem:flyway-sql-exception");
        dataSource.setUser("user");
        dataSource.setPassword("password");
        return dataSource;
    }
}
