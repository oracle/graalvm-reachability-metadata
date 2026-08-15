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

    private static final String ORACLE_JDBC_URL = "jdbc:oracle:thin:@localhost:1521/FREEPDB1";

    @Test
    void discoversOracleDatabaseType() {
        FluentConfiguration configuration = Flyway.configure();
        DatabaseType databaseType = getOracleDatabaseType(configuration);

        assertThat(databaseType.handlesJDBCUrl(ORACLE_JDBC_URL)).isTrue();
        assertThat(databaseType.handlesJDBCUrl("jdbc:postgresql://localhost/test")).isFalse();
        assertThat(databaseType.getDriverClass(
                        ORACLE_JDBC_URL, FlywayDatabaseOracleTests.class.getClassLoader()))
                .isEqualTo("oracle.jdbc.OracleDriver");
    }

    @Test
    void bindsAllOracleConfigurationFields() {
        FluentConfiguration configuration = Flyway.configure()
                .configuration(Map.of(
                        "flyway.oracle.sqlplus", "true",
                        "flyway.oracle.sqlplusWarn", "true",
                        "flyway.oracle.kerberosCacheFile", "flyway-kerberos-cache",
                        "flyway.oracle.walletLocation", "flyway-wallet"));

        OracleConfigurationExtension extension = configuration
                .getConfigurationExtension(OracleConfigurationExtension.class);

        assertThat(extension.getSqlplus()).isTrue();
        assertThat(extension.getSqlplusWarn()).isTrue();
        assertThat(extension.getKerberosCacheFile()).isEqualTo("flyway-kerberos-cache");
        assertThat(extension.getWalletLocation()).isEqualTo("flyway-wallet");
    }

    private static DatabaseType getOracleDatabaseType(FluentConfiguration configuration) {
        return configuration.getPluginRegister()
                .getInstancesOf(DatabaseType.class)
                .stream()
                .filter(type -> type.getName().equals("Oracle"))
                .findFirst()
                .orElseThrow();
    }
}
