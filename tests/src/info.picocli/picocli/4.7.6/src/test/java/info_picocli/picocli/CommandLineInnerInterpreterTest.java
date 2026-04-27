/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package info_picocli.picocli;

import java.io.ByteArrayInputStream;
import java.io.Console;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Option;

import sun.misc.Unsafe;

import static org.assertj.core.api.Assertions.assertThat;

public class CommandLineInnerInterpreterTest {

    @Test
    void expandsArrayOptionValues() {
        ArrayOptionCommand command = new ArrayOptionCommand();

        new CommandLine(command).parseArgs("--tag", "alpha", "--tag", "beta,gamma");

        assertThat(command.tags).containsExactly("alpha", "beta", "gamma");
    }

    @Test
    void togglesOptionalBooleanFromInitialValue() {
        OptionalBooleanCommand command = new OptionalBooleanCommand();

        new CommandLine(command).parseArgs("--enabled");

        assertThat(command.enabled).contains(true);
    }

    @Test
    void readsInteractivePasswordWithoutEcho() throws Exception {
        PasswordCommand command = new PasswordCommand();
        InputStream originalIn = System.in;
        ConsoleState consoleState = replaceSystemConsole(newUninitializedConsole());
        System.setIn(new ByteArrayInputStream("s3cr3t\n".getBytes(StandardCharsets.UTF_8)));
        try {
            new CommandLine(command).parseArgs("--password");
        } finally {
            System.setIn(originalIn);
            consoleState.restore();
        }

        assertThat(command.password).containsExactly('s', '3', 'c', 'r', '3', 't');
    }

    private static Console newUninitializedConsole() throws Exception {
        return (Console) unsafe().allocateInstance(Console.class);
    }

    private static ConsoleState replaceSystemConsole(Console console) throws Exception {
        Field consoleField = System.class.getDeclaredField("cons");
        Unsafe unsafe = unsafe();
        Object base = unsafe.staticFieldBase(consoleField);
        long offset = unsafe.staticFieldOffset(consoleField);
        Object previous = unsafe.getObjectVolatile(base, offset);
        unsafe.putObjectVolatile(base, offset, console);
        return new ConsoleState(unsafe, base, offset, previous);
    }

    private static Unsafe unsafe() throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return (Unsafe) unsafeField.get(null);
    }

    private static final class ConsoleState {
        private final Unsafe unsafe;
        private final Object base;
        private final long offset;
        private final Object previous;

        private ConsoleState(Unsafe unsafe, Object base, long offset, Object previous) {
            this.unsafe = unsafe;
            this.base = base;
            this.offset = offset;
            this.previous = previous;
        }

        private void restore() {
            unsafe.putObjectVolatile(base, offset, previous);
        }
    }

    public static class ArrayOptionCommand {
        @Option(names = "--tag", split = ",")
        private String[] tags;
    }

    public static class OptionalBooleanCommand {
        @Option(names = "--enabled", arity = "0..1")
        private Optional<Boolean> enabled = Optional.of(false);
    }

    public static class PasswordCommand {
        @Option(names = "--password", arity = "0", interactive = true)
        private char[] password;
    }
}
