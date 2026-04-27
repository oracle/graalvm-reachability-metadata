/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package info_picocli.picocli;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import static org.assertj.core.api.Assertions.assertThat;

public class CommandLineInnerModelInnerCommandUserObjectTest {

    @Test
    void populateSpecCreatesProxyForAnnotatedInterfaceCommand() {
        ProxyBackedCommand command = CommandLine.populateSpec(
                ProxyBackedCommand.class,
                "--name",
                "metadata",
                "--count",
                "3",
                "--verbose",
                "library");

        assertThat(command.name()).isEqualTo("metadata");
        assertThat(command.count()).isEqualTo(3);
        assertThat(command.verbose()).isTrue();
        assertThat(command.target()).isEqualTo("library");
    }

    @Command(name = "proxy-backed")
    public interface ProxyBackedCommand {
        @Option(names = "--name")
        String name();

        @Option(names = "--count")
        int count();

        @Option(names = "--verbose")
        boolean verbose();

        @Parameters(index = "0")
        String target();
    }
}
