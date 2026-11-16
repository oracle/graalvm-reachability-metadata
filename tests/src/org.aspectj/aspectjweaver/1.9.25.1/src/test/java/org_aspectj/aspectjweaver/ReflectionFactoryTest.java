/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import org.aspectj.bridge.ICommand;
import org.aspectj.bridge.IMessageHandler;
import org.aspectj.bridge.MessageHandler;
import org.aspectj.bridge.ReflectionFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionFactoryTest {
    @Test
    void constructsAndRunsNamedCommand() {
        MessageHandler messages = new MessageHandler();
        ICommand command = ReflectionFactory.makeCommand(RecordingCommand.class.getName(), messages);

        assertThat(command).isInstanceOf(RecordingCommand.class);
        assertThat(command.runCommand(new String[] {"compile", "woven"}, messages)).isTrue();
        assertThat(((RecordingCommand) command).arguments).containsExactly("compile", "woven");
        assertThat(command.repeatCommand(messages)).isTrue();
        assertThat(messages.getUnmodifiableListView()).isEmpty();
    }

    public static class RecordingCommand implements ICommand {
        private String[] arguments;

        public RecordingCommand() {
        }

        @Override
        public boolean runCommand(String[] args, IMessageHandler handler) {
            arguments = args.clone();
            return arguments.length > 0;
        }

        @Override
        public boolean repeatCommand(IMessageHandler handler) {
            return runCommand(arguments, handler);
        }
    }
}
