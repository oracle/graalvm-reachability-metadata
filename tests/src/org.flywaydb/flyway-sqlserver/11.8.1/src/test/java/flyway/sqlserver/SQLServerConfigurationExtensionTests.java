/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package flyway.sqlserver;

import java.util.Map;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.database.sqlserver.SQLServerConfigurationExtension;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SQLServerConfigurationExtensionTests {

    @Test
    void bindsNestedKerberosConfiguration() {
        FluentConfiguration configuration = Flyway.configure()
                .configuration(Map.of(
                        "flyway.sqlserver.kerberos.login.file",
                        "sqlserver-login.conf"));

        SQLServerConfigurationExtension sqlServerConfiguration =
                configuration.getConfigurationExtension(SQLServerConfigurationExtension.class);

        assertThat(sqlServerConfiguration.getKerberos().getLogin().getFile())
                .isEqualTo("sqlserver-login.conf");
    }
}
