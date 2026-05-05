/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_wagon.wagon_provider_api;

import org.apache.maven.wagon.CommandExecutor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CommandExecutorAnonymous1Test {
    @Test
    void initializesCommandExecutorRole() {
        assertThat(CommandExecutor.ROLE).isEqualTo(CommandExecutor.class.getName());
    }
}
