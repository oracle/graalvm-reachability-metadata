/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package flyway.sqlserver;

import java.util.Map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DryRunConfigurationExtensionStubTest {

    @Test
    void preservesDryRunDefaultWhileApplyingSqlServerConfiguration() {
        Flyway flyway = Flyway.configure()
                .configuration(Map.of(
                        "flyway.sqlserver.kerberos.login.file", "sqlserver-login.conf"))
                .load();

        assertThat(flyway.getConfiguration().getDryRunOutput()).isNull();
    }
}
