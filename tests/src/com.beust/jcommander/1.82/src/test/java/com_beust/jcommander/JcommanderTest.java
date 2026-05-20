/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_beust.jcommander;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.converters.IntegerConverter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JcommanderTest {
    @Test
    void createsConsoleThroughJdkConsoleLookup() {
        JCommander commander = new JCommander();

        assertThat(commander.getConsole()).isNotNull();
    }

    @Test
    void resolvesCommandDescriptionFromParametersResourceBundle() {
        JCommander commander = new JCommander(new RootCommand());
        commander.addCommand(new LocalizedCommand());

        String description = commander.getUsageFormatter().getCommandDescription("localized");

        assertThat(description).isEqualTo("localized command description");
    }

    @Test
    void parsesParameterWithStringConstructorConverter() {
        ConvertedOptions options = new ConvertedOptions();

        JCommander.newBuilder()
                .addObject(options)
                .build()
                .parse("--count", "42");

        assertThat(options.count).isEqualTo(42);
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

    public static class ConvertedOptions {
        @Parameter(names = "--count", converter = IntegerConverter.class)
        public Integer count;
    }
}
