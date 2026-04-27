/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package info_picocli.picocli;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Option;

import static org.assertj.core.api.Assertions.assertThat;

public class CommandLineInnerModelInnerArgGroupSpecTest {

    @Test
    void appendsRepeatedArgGroupInstancesToArrayField() {
        ArrayGroupCommand command = new ArrayGroupCommand();

        new CommandLine(command).parseArgs("--name", "alpha", "--count", "1", "--name", "beta", "--count", "2");

        assertThat(command.groups).hasSize(2);
        assertThat(command.groups[0].name).isEqualTo("alpha");
        assertThat(command.groups[0].count).isEqualTo(1);
        assertThat(command.groups[1].name).isEqualTo("beta");
        assertThat(command.groups[1].count).isEqualTo(2);
    }

    public static class ArrayGroupCommand {
        @ArgGroup(exclusive = false, multiplicity = "0..*")
        private RepeatedGroup[] groups;
    }

    public static class RepeatedGroup {
        @Option(names = "--name", required = true)
        private String name;

        @Option(names = "--count", required = true)
        private int count;
    }
}
