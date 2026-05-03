/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package flyway;

import java.util.Map;

import org.flywaydb.core.api.configuration.ClassicConfiguration;
import org.flywaydb.core.internal.command.clean.CleanModel;
import org.flywaydb.core.internal.configuration.ConfigUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassicConfigurationTest {

    @Test
    void configuresNestedPluginExtensionProperties() {
        final ClassicConfiguration configuration = new ClassicConfiguration();

        configuration.configure(Map.of(
                "flyway.plugins.clean.schemas.exclude", "audit,temporary"));

        final CleanModel cleanModel = ConfigUtils.getCleanModel(configuration);

        assertThat(cleanModel).isNotNull();
        assertThat(cleanModel.getSchemas()).isNotNull();
        assertThat(cleanModel.getSchemas().getExclude()).containsExactly("audit", "temporary");
    }
}
