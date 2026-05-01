/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aesh.readline;

import java.io.Console;
import java.lang.reflect.Field;

import org.aesh.readline.terminal.TerminalBuilder;
import org.aesh.terminal.Terminal;
import org.junit.jupiter.api.Test;

import sun.misc.Unsafe;

import static org.assertj.core.api.Assertions.assertThat;

public class TerminalBuilderTest {
    @Test
    void windowsSystemTerminalChecksWhetherConsoleIsATerminal() throws Exception {
        Unsafe unsafe = unsafe();
        StaticObjectSlot systemConsole = staticObjectSlot(unsafe, System.class, "cons");
        StaticBooleanSlot consoleIsTerminal = staticBooleanSlot(unsafe, Console.class, "istty");
        Object originalSystemConsole = systemConsole.get();
        boolean originalConsoleIsTerminal = consoleIsTerminal.get();
        String originalOsName = System.getProperty("os.name");
        Console console = (Console) unsafe.allocateInstance(Console.class);

        try {
            System.setProperty("os.name", "Windows 10");
            systemConsole.set(console);
            consoleIsTerminal.set(false);

            Terminal terminal = TerminalBuilder.builder()
                    .name("dynamic-access terminal")
                    .system(true)
                    .nativeSignals(false)
                    .build();

            assertThat(terminal).isNotNull();
            assertThat(terminal.getClass().getName())
                    .isEqualTo("org.aesh.readline.terminal.impl.WinExternalTerminal");
        } finally {
            restoreProperty("os.name", originalOsName);
            systemConsole.set(originalSystemConsole);
            consoleIsTerminal.set(originalConsoleIsTerminal);
        }
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static StaticObjectSlot staticObjectSlot(Unsafe unsafe, Class<?> holder, String fieldName) throws Exception {
        Field field = holder.getDeclaredField(fieldName);
        return new StaticObjectSlot(unsafe, unsafe.staticFieldBase(field), unsafe.staticFieldOffset(field));
    }

    private static StaticBooleanSlot staticBooleanSlot(Unsafe unsafe, Class<?> holder, String fieldName) throws Exception {
        Field field = holder.getDeclaredField(fieldName);
        return new StaticBooleanSlot(unsafe, unsafe.staticFieldBase(field), unsafe.staticFieldOffset(field));
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    private static final class StaticObjectSlot {
        private final Unsafe unsafe;
        private final Object base;
        private final long offset;

        private StaticObjectSlot(Unsafe unsafe, Object base, long offset) {
            this.unsafe = unsafe;
            this.base = base;
            this.offset = offset;
        }

        private Object get() {
            return unsafe.getObject(base, offset);
        }

        private void set(Object value) {
            unsafe.putObject(base, offset, value);
        }
    }

    private static final class StaticBooleanSlot {
        private final Unsafe unsafe;
        private final Object base;
        private final long offset;

        private StaticBooleanSlot(Unsafe unsafe, Object base, long offset) {
            this.unsafe = unsafe;
            this.base = base;
            this.offset = offset;
        }

        private boolean get() {
            return unsafe.getBoolean(base, offset);
        }

        private void set(boolean value) {
            unsafe.putBoolean(base, offset, value);
        }
    }
}
