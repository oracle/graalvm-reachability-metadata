/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_component_api;

import org.codehaus.plexus.logging.LoggerManager;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LoggerManagerAnonymous1Test {
    @Test
    void roleNameInitializesLoggerManagerClassLiteralHelper() {
        assertThat(LoggerManager.ROLE).isEqualTo(LoggerManager.class.getName());
    }
}
