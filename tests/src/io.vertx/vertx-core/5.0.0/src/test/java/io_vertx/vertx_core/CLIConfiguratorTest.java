/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_vertx.vertx_core;

import io.vertx.core.cli.CLI;
import io.vertx.core.cli.CommandLine;
import io.vertx.core.cli.annotations.Argument;
import io.vertx.core.cli.annotations.CLIConfigurator;
import io.vertx.core.cli.annotations.Name;
import io.vertx.core.cli.annotations.Option;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CLIConfiguratorTest {

    @Test
    void injectSetsMultiValueOptionArrayAndPositionalArgument() {
        CLI cli = CLI.create(AnnotatedCommand.class);
        CommandLine commandLine = cli.parse(Arrays.asList("--tag=alpha", "--tag=beta", "destination"));
        AnnotatedCommand command = new AnnotatedCommand();

        CLIConfigurator.inject(commandLine, command);

        assertArrayEquals(new String[] {"alpha", "beta"}, command.tags());
        assertEquals("destination", command.target());
    }

    @Name("annotated-command")
    public static class AnnotatedCommand {
        private String[] tags;
        private String target;

        @Option(longName = "tag", acceptMultipleValues = true)
        public void setTags(String[] tags) {
            this.tags = tags;
        }

        @Argument(index = 0)
        public void setTarget(String target) {
            this.target = target;
        }

        String[] tags() {
            return tags;
        }

        String target() {
            return target;
        }
    }
}
