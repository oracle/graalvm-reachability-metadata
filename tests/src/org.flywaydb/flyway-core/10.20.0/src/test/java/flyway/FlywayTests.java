/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package flyway;

import java.nio.charset.StandardCharsets;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.MigrateResult;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FlywayTests {

    @Test
    void migrate() {
        DataSource dataSource = getDataSource();

        Configuration configuration = new FluentConfiguration()
                .dataSource(dataSource)
                .encoding(StandardCharsets.UTF_8)
                .resourceProvider(new FixedResourceProvider());

        Flyway flyway = new Flyway(configuration);
        MigrateResult migration = flyway.migrate();

        assertThat(migration.success).isTrue();
        assertThat(migration.migrationsExecuted).isEqualTo(2);
    }

    private DataSource getDataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setUrl("jdbc:h2:mem:test");
        dataSource.setUser("user");
        dataSource.setPassword("password");
        return dataSource;
    }
}
