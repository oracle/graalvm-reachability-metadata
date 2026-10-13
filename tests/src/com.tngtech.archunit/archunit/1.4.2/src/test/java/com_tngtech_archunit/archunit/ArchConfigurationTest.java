/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tngtech_archunit.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.ArchConfiguration;
import org.junit.jupiter.api.Test;

public class ArchConfigurationTest {
    @Test
    void resettingConfigurationReloadsDefaultsFromTheApplicationClassPath() {
        ArchConfiguration configuration = ArchConfiguration.get();
        boolean configuredDefault = configuration.resolveMissingDependenciesFromClassPath();
        configuration.setResolveMissingDependenciesFromClassPath(!configuredDefault);

        configuration.reset();

        assertThat(configuration.resolveMissingDependenciesFromClassPath())
                .isEqualTo(configuredDefault);
    }
}
