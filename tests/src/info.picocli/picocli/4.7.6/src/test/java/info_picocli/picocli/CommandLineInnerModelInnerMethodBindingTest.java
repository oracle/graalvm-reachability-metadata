/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package info_picocli.picocli;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Option;

import static org.assertj.core.api.Assertions.assertThat;

public class CommandLineInnerModelInnerMethodBindingTest {

    @Test
    void parseArgsInvokesAnnotatedSetterMethods() {
        SetterMethodCommand command = new SetterMethodCommand();

        new CommandLine(command).parseArgs("--name", "picocli", "--count", "3");

        assertThat(command.names()).containsExactly("picocli");
        assertThat(command.count()).isEqualTo(3);
    }

    public static class SetterMethodCommand {
        private final List<String> names = new ArrayList<>();
        private int count;

        @Option(names = "--name")
        public void addName(String name) {
            names.add(name);
        }

        @Option(names = "--count")
        public void updateCount(int count) {
            this.count = count;
        }

        List<String> names() {
            return names;
        }

        int count() {
            return count;
        }
    }
}
