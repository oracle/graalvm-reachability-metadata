/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package flyway;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydb.core.internal.exception.FlywaySqlException;
import org.flywaydb.core.internal.exception.sqlExceptions.FlywaySqlNoDriversForInteractiveAuthException;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FlywayTests {

    @Test
    void migrate() {
        DataSource dataSource = getDataSource();

        Configuration configuration = new FluentConfiguration()
                .dataSource(dataSource)
                .encoding(StandardCharsets.UTF_8)
                .resourceProvider(new FixedResourceProvider())
                .loggers("slf4j", "log4j2", "apache-commons");

        Flyway flyway = new Flyway(configuration);
        MigrateResult migration = flyway.migrate();

        assertThat(migration.success).isTrue();
        assertThat(migration.migrationsExecuted).isEqualTo(2);
    }

    /**
     * Verifies that the {@link FlywaySqlException} subclasses are reflectively accessible in GraalVM native image.
     * {@link FlywaySqlException#throwFlywayExceptionIfPossible} uses reflection to invoke {@code isFlywaySpecificVersionOf(SQLException)}
     * on each registered subclass in order. Without the reachability metadata, this fails with {@link NoSuchMethodException}.
     * <p>
     * Using a {@link SQLException} that matches {@link FlywaySqlNoDriversForInteractiveAuthException}
     * (last in the list) causes all three registered subclasses to be checked via reflection before
     * the matching one is found and thrown.
     */
    @Test
    void flywaySqlExceptionSubclassesAreReflectivelyAccessible() {
        DataSource dataSource = getDataSource();
        SQLException msal4jException = new SQLException("MSAL4J drivers are missing");

        assertThatThrownBy(() -> FlywaySqlException.throwFlywayExceptionIfPossible(msal4jException, dataSource))
                .isInstanceOf(FlywaySqlNoDriversForInteractiveAuthException.class);
    }

    private DataSource getDataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setUrl("jdbc:h2:mem:test");
        dataSource.setUser("user");
        dataSource.setPassword("password");
        return dataSource;
    }
}
