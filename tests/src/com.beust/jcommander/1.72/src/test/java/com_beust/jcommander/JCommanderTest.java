/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_beust.jcommander;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameters;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JCommanderTest {
    @Test
    void createsConsoleThroughPublicAccessor() {
        assertThat(JCommander.getConsole()).isNotNull();
    }

    @Test
    void loadsCommandDescriptionFromCommandResourceBundle() {
        JCommander commander = JCommander.newBuilder()
                .addCommand(new LocalizedCommand())
                .build();

        assertThat(commander.getCommandDescription("localized"))
                .isEqualTo("Localized command description loaded from command resource bundle");
    }

    @Parameters(
            commandNames = "localized",
            commandDescription = "Fallback command description",
            commandDescriptionKey = "command.description",
            resourceBundle = "com_beust.jcommander.parameter_descriptions")
    public static class LocalizedCommand {
    }
}
