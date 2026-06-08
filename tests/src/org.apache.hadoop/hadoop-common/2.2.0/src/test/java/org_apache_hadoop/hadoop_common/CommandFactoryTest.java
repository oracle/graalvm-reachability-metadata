/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.shell.Command;
import org.apache.hadoop.fs.shell.CommandFactory;
import org.junit.jupiter.api.Test;

public class CommandFactoryTest {
    @Test
    void registerCommandsInvokesRegistrarAndCreatesRegisteredCommand() {
        Configuration conf = new Configuration(false);
        CommandFactory factory = new CommandFactory(conf);

        factory.registerCommands(Registrar.class);

        assertThat(factory.getNames()).containsExactly("-registered");
        Command command = factory.getInstance("-registered");
        assertThat(command).isInstanceOf(RegisteredCommand.class);
        assertThat(command.getConf()).isSameAs(conf);
        assertThat(command.getName()).isEqualTo("registered");
        assertThat(command.getUsage()).isEqualTo("-registered <path>");
        assertThat(command.getDescription()).isEqualTo("Command registered through CommandFactory.");
    }

    public static class Registrar {
        public static void registerCommands(CommandFactory factory) {
            factory.addClass(RegisteredCommand.class, "-registered");
        }
    }

    public static class RegisteredCommand extends Command {
        public static final String NAME = "registered";
        public static final String USAGE = "<path>";
        public static final String DESCRIPTION = "Command registered through CommandFactory.";

        @Override
        public String getCommandName() {
            return NAME;
        }

        @Override
        protected void run(Path path) throws IOException {
        }
    }
}
