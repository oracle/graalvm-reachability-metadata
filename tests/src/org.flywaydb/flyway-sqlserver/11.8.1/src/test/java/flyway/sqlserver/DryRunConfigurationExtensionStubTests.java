/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package flyway.sqlserver;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DryRunConfigurationExtensionStubTests {

    @Test
    void copiesConfigurationThroughFluentApi() {
        FluentConfiguration source = Flyway.configure()
                .table("source_schema_history");

        FluentConfiguration copy = Flyway.configure()
                .configuration(source);

        assertThat(copy.getTable()).isEqualTo("source_schema_history");
        assertThat(copy.getDryRunOutput()).isNull();
    }
}
