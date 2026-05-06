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

public class JcommanderTest {
    @Test
    void createsConsoleThroughJdkConsoleLookup() {
        assertThat(JCommander.getConsole()).isNotNull();
    }

    @Test
    void resolvesCommandDescriptionFromParametersResourceBundle() {
        JCommander commander = new JCommander(new RootCommand());
        commander.addCommand(new LocalizedCommand());

        String description = commander.getCommandDescription("localized");

        assertThat(description).isEqualTo("localized command description");
    }

    public static class RootCommand {
    }

    @Parameters(
            commandNames = "localized",
            commandDescription = "fallback command description",
            commandDescriptionKey = "command.description",
            resourceBundle = "com_beust.jcommander.parameter_descriptions")
    public static class LocalizedCommand {
    }
}
