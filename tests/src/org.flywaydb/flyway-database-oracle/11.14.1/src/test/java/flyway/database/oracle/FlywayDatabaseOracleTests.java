/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package flyway.database.oracle;

import java.util.Map;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.internal.database.DatabaseType;
import org.flywaydb.database.oracle.OracleConfigurationExtension;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// Exercises Flyway Oracle plugin discovery and configuration through consumer-facing APIs.
public class FlywayDatabaseOracleTests {

    @Test
    void discoversAndConfiguresOraclePlugins() {
        FluentConfiguration configuration = Flyway.configure()
                .configuration(Map.of(
                        "flyway.oracle.sqlplus", "true",
                        "flyway.oracle.sqlplusWarn", "true"));

        DatabaseType databaseType = configuration.getPluginRegister()
                .getInstancesOf(DatabaseType.class)
                .stream()
                .filter(type -> type.getName().equals("Oracle"))
                .findFirst()
                .orElseThrow();
        OracleConfigurationExtension extension = configuration
                .getConfigurationExtension(OracleConfigurationExtension.class);

        assertThat(databaseType.handlesJDBCUrl("jdbc:oracle:thin:@localhost:1521/FREEPDB1"))
                .isTrue();
        assertThat(extension.getSqlplus()).isTrue();
        assertThat(extension.getSqlplusWarn()).isTrue();
    }
}
