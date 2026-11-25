/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.sql.Connection;
import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DataSourceLinkFactoryTest {
    @Test
    void wrapsGlobalDatabaseWithLocalCredentials() throws Exception {
        try (DataSourceSupport source = new DataSourceSupport("linked-database");
                Connection connection = source.linked().getConnection()) {
            assertThat(connection.getMetaData().getUserName()).isEqualTo("READER");
            DataSource unwrapped = source.linked().unwrap(DataSource.class);
            assertThat(unwrapped).isNotSameAs(source.linked());
            assertThat(((JdbcDataSource) unwrapped).getURL())
                    .isEqualTo(((JdbcDataSource) source.original()).getURL());
        }
    }
}
