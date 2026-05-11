/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_beust.jcommander;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
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
    void instantiatesParameterConverterThroughDefaultConstructor() {
        DefaultConstructorConverterCommand command = new DefaultConstructorConverterCommand();

        new JCommander(command, "--answer", "41");

        assertThat(command.answer).isEqualTo(42);
    }

    @Test
    void instantiatesParameterConverterThroughOptionNameConstructor() {
        OptionNameConstructorConverterCommand command = new OptionNameConstructorConverterCommand();

        new JCommander(command, "--count", "7");

        assertThat(command.count).isEqualTo("--count=7");
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

    public static class DefaultConstructorConverterCommand {
        @Parameter(names = "--answer", converter = DefaultConstructorIntegerConverter.class)
        private Integer answer;
    }

    public static class DefaultConstructorIntegerConverter implements IStringConverter<Integer> {
        @Override
        public Integer convert(String value) {
            return Integer.parseInt(value) + 1;
        }
    }

    public static class OptionNameConstructorConverterCommand {
        @Parameter(names = "--count", converter = OptionNameStringConverter.class)
        private String count;
    }

    public static class OptionNameStringConverter implements IStringConverter<String> {
        private final String optionName;

        public OptionNameStringConverter(String optionName) {
            this.optionName = optionName;
        }

        @Override
        public String convert(String value) {
            return optionName + "=" + value;
        }
    }
}
