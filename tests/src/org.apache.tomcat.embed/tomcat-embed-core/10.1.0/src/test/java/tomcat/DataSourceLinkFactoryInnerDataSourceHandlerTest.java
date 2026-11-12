/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DataSourceLinkFactoryInnerDataSourceHandlerTest {
    @Test
    void dispatchesDatabaseMethodsThroughCredentialAwareProxy() throws Exception {
        try (DataSourceSupport source = new DataSourceSupport("handler-database");
                Connection connection = source.linked().getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT CURRENT_USER")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo("READER");
            assertThat(source.linked().getLoginTimeout()).isZero();
        }
    }
}
