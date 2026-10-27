/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import oracle.jdbc.driver.cli.urlbuilder.BuildUrlCommand;
import org.junit.jupiter.api.Test;

public class BuildUrlCommandTest {
    @Test
    void exposesJdbcPropertiesForUrlConstruction() {
        BuildUrlCommand command = new BuildUrlCommand();

        Map<Integer, String> properties = command.getPossibleProperties(false, false);

        assertThat(command.getName()).isEqualTo("build-url");
        assertThat(properties).isNotEmpty().containsValue("user");
    }
}
