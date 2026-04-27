/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package info_picocli.picocli;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;

import static org.assertj.core.api.Assertions.assertThat;

public class CommandLineInnerBuiltInInnerClassConverterTest {

    @Test
    void convertsFullyQualifiedClassNameOptionToClass() {
        OptionSpec targetClass = OptionSpec.builder("--target-class")
                .type(Class.class)
                .required(true)
                .build();
        CommandSpec commandSpec = CommandSpec.create().addOption(targetClass);

        new CommandLine(commandSpec).parseArgs("--target-class", "java.lang.String");

        Class<?> parsedClass = targetClass.getValue();

        assertThat(parsedClass).isEqualTo(String.class);
    }
}
