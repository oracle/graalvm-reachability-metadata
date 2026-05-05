/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package args4j.args4j;

import org.junit.jupiter.api.Test;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.SubCommand;
import org.kohsuke.args4j.spi.SubCommandHandler;
import org.kohsuke.args4j.spi.SubCommands;

import static org.assertj.core.api.Assertions.assertThat;

public class SubCommandHandlerTest {
    @Test
    void instantiatesAndParsesSelectedSubCommand() throws CmdLineException {
        CheckoutCommand.reset();
        GitOptions options = new GitOptions();

        new CmdLineParser(options).parseArgument(
                "-verbose", "checkout", "-branch", "native-image", "-force");

        assertThat(options.verbose).isTrue();
        assertThat(options.command).isInstanceOf(CheckoutCommand.class);
        CheckoutCommand checkout = (CheckoutCommand) options.command;
        assertThat(checkout.branch).isEqualTo("native-image");
        assertThat(checkout.force).isTrue();
        assertThat(CheckoutCommand.instancesCreated).isEqualTo(1);
    }

    public interface Command {
    }

    public static class GitOptions {
        @Option(name = "-verbose")
        public boolean verbose;

        @Argument(handler = SubCommandHandler.class)
        @SubCommands({
                @SubCommand(name = "checkout", impl = CheckoutCommand.class)
        })
        public Command command;
    }

    public static class CheckoutCommand implements Command {
        private static int instancesCreated;

        @Option(name = "-branch", required = true)
        public String branch;

        @Option(name = "-force")
        public boolean force;

        public CheckoutCommand() {
            instancesCreated++;
        }

        public static void reset() {
            instancesCreated = 0;
        }
    }
}
