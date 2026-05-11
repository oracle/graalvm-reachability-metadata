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
        assertThat(new JCommander().getConsole()).isNotNull();
    }

    @Test
    void resolvesCommandDescriptionFromParametersResourceBundle() {
        JCommander commander = new JCommander(new RootCommand());
        commander.addCommand(new LocalizedCommand());

        StringBuilder usage = new StringBuilder();
        commander.getUsageFormatter().usage(usage);

        assertThat(usage.toString()).contains("localized command description");
    }

    @Test
    void parsesParameterWithStringConverterConstructor() {
        ConvertedOptions options = new ConvertedOptions();

        JCommander.newBuilder()
                .addObject(options)
                .build()
                .parse("--converted", "alpha");

        assertThat(options.converted.value).isEqualTo("--converted=alpha");
    }

    public static class RootCommand {
    }

    public static class ConvertedOptions {
        @Parameter(names = "--converted", converter = OptionNameConverter.class)
        private ConvertedValue converted;
    }

    public static class ConvertedValue {
        private final String value;

        public ConvertedValue(String value) {
            this.value = value;
        }
    }

    public static class OptionNameConverter implements IStringConverter<ConvertedValue> {
        private final String optionName;

        public OptionNameConverter(String optionName) {
            this.optionName = optionName;
        }

        @Override
        public ConvertedValue convert(String value) {
            return new ConvertedValue(optionName + "=" + value);
        }
    }

    @Parameters(
            commandNames = "localized",
            commandDescription = "fallback command description",
            commandDescriptionKey = "command.description",
            resourceBundle = "com_beust.jcommander.parameter_descriptions")
    public static class LocalizedCommand {
    }
}
