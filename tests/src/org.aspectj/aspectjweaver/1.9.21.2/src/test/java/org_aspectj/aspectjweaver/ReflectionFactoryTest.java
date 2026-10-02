/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.aspectj.bridge.ICommand;
import org.aspectj.bridge.IMessage;
import org.aspectj.bridge.IMessageHandler;
import org.aspectj.bridge.ReflectionFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionFactoryTest {
    @Test
    void createsCommandUsingPublicNoArgumentConstructor() {
        CollectingMessageHandler messages = new CollectingMessageHandler();

        ICommand command = ReflectionFactory.makeCommand(RecordingCommand.class.getName(), messages);

        assertThat(command).isInstanceOf(RecordingCommand.class);
        assertThat(messages.messages()).isEmpty();
        assertThat(command.runCommand(new String[] {"compile", "woven"}, messages)).isTrue();
        assertThat(((RecordingCommand) command).arguments()).containsExactly("compile", "woven");
    }

    @Test
    void createsCommandFromAlternateFactoryPathUsingEmptyArgumentArray() throws ReflectiveOperationException {
        CollectingMessageHandler messages = new CollectingMessageHandler();

        ICommand command = invokeFactoryMake(EmptyArgumentsCommand.class.getName(), new Object[0], messages);

        assertThat(command).isInstanceOf(EmptyArgumentsCommand.class);
        assertThat(messages.messages()).isEmpty();
        assertThat(command.repeatCommand(messages)).isTrue();
    }

    private static ICommand invokeFactoryMake(String className, Object[] args, IMessageHandler messages)
            throws ReflectiveOperationException {
        Method factoryMethod = ReflectionFactory.class.getDeclaredMethod(
                "make",
                Class.class,
                String.class,
                Object[].class,
                IMessageHandler.class
        );
        factoryMethod.setAccessible(true);
        return (ICommand) factoryMethod.invoke(null, ICommand.class, className, args, messages);
    }

    public static final class RecordingCommand implements ICommand {
        private String[] arguments = new String[0];

        public RecordingCommand() {
        }

        @Override
        public boolean runCommand(String[] args, IMessageHandler handler) {
            arguments = Arrays.copyOf(args, args.length);
            return true;
        }

        @Override
        public boolean repeatCommand(IMessageHandler handler) {
            return runCommand(arguments, handler);
        }

        String[] arguments() {
            return Arrays.copyOf(arguments, arguments.length);
        }
    }

    public static final class EmptyArgumentsCommand implements ICommand {
        private boolean repeated;

        public EmptyArgumentsCommand() {
        }

        @Override
        public boolean runCommand(String[] args, IMessageHandler handler) {
            return true;
        }

        @Override
        public boolean repeatCommand(IMessageHandler handler) {
            repeated = true;
            return repeated;
        }
    }

    private static final class CollectingMessageHandler implements IMessageHandler {
        private final List<IMessage> messages = new ArrayList<>();

        @Override
        public boolean handleMessage(IMessage message) {
            messages.add(message);
            return true;
        }

        @Override
        public boolean isIgnoring(IMessage.Kind kind) {
            return false;
        }

        @Override
        public void dontIgnore(IMessage.Kind kind) {
        }

        @Override
        public void ignore(IMessage.Kind kind) {
        }

        List<IMessage> messages() {
            return messages;
        }
    }
}
