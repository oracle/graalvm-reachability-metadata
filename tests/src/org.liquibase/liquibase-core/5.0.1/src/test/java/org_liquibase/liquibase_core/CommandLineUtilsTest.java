/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.integration.commandline.CommandLineUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CommandLineUtilsTest {

    @Test
    void getBannerInitializesCommandLineResources() {
        final String banner = CommandLineUtils.getBanner();

        assertThat(banner).contains("Liquibase");
    }
}
