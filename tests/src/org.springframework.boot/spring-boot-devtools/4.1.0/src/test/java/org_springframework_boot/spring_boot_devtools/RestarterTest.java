/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_devtools;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.devtools.restart.RestartInitializer;
import org.springframework.boot.devtools.restart.Restarter;

import static org.assertj.core.api.Assertions.assertThat;

public class RestarterTest {

    @AfterEach
    void clearRestarter() {
        Restarter.clearInstance();
    }

    @Test
    void initializesRestartSupportWithConfiguredInitializer() {
        Restarter.initialize(new String[0], false, RestartInitializer.NONE, false);

        assertThat(Restarter.getInstance()).isNotNull();
    }
}
