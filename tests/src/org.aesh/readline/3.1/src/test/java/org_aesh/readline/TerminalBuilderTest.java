/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aesh.readline;

import org.aesh.terminal.tty.TerminalBuilder;
import org.aesh.terminal.Terminal;
import org.aesh.terminal.utils.OSUtils;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Console;
import java.lang.reflect.Field;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

public class TerminalBuilderTest {
    private static final String WINDOWS_OS_NAME = "Windows 11";

    @Test
    void windowsSystemTerminalChecksWhetherConsoleIsATerminal() throws Exception {
        if (!hasConsoleIsTerminalMethod()) {
            assertExternalTerminalCanBeBuilt();
            return;
        }

        Unsafe unsafe = getUnsafe();
        String originalOsName = System.getProperty("os.name");
        boolean originalIsWindows = isWindows(originalOsName);
        boolean originalIsCygwin = originalIsWindows
                && System.getenv("PWD") != null
                && System.getenv("PWD").startsWith("/");
        Field systemConsole = System.class.getDeclaredField("cons");
        Object originalSystemConsole = readStaticObject(unsafe, systemConsole);

        try {
            System.setProperty("os.name", WINDOWS_OS_NAME);
            Class.forName(OSUtils.class.getName());
            writeStaticBoolean(unsafe, OSUtils.class.getField("IS_WINDOWS"), true);
            writeStaticBoolean(unsafe, OSUtils.class.getField("IS_CYGWIN"), false);
            writeStaticObject(unsafe, systemConsole, unsafe.allocateInstance(Console.class));

            Terminal terminal = TerminalBuilder.builder()
                    .name("synthetic windows console")
                    .type("xterm")
                    .input(new ByteArrayInputStream(new byte[0]))
                    .output(new ByteArrayOutputStream())
                    .system(true)
                    .nativeSignals(false)
                    .build();
            try {
                assertThat(terminal.getName()).isEqualTo("synthetic windows console");
            } finally {
                terminal.close();
            }
        } finally {
            restoreOsName(originalOsName);
            writeStaticBoolean(unsafe, OSUtils.class.getField("IS_WINDOWS"), originalIsWindows);
            writeStaticBoolean(unsafe, OSUtils.class.getField("IS_CYGWIN"), originalIsCygwin);
            writeStaticObject(unsafe, systemConsole, originalSystemConsole);
        }
    }

    private static void assertExternalTerminalCanBeBuilt() throws Exception {
        Terminal terminal = TerminalBuilder.builder()
                .name("external console")
                .input(new ByteArrayInputStream(new byte[0]))
                .output(new ByteArrayOutputStream())
                .system(false)
                .build();
        try {
            assertThat(terminal.getName()).isEqualTo("external console");
        } finally {
            terminal.close();
        }
    }

    private static boolean hasConsoleIsTerminalMethod() {
        try {
            Console.class.getMethod("isTerminal");
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private static Unsafe getUnsafe() throws ReflectiveOperationException {
        Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        return (Unsafe) theUnsafe.get(null);
    }

    private static Object readStaticObject(Unsafe unsafe, Field field) {
        return unsafe.getObjectVolatile(unsafe.staticFieldBase(field), unsafe.staticFieldOffset(field));
    }

    private static void writeStaticObject(Unsafe unsafe, Field field, Object value) {
        unsafe.putObjectVolatile(unsafe.staticFieldBase(field), unsafe.staticFieldOffset(field), value);
    }

    private static void writeStaticBoolean(Unsafe unsafe, Field field, boolean value) {
        unsafe.putBooleanVolatile(unsafe.staticFieldBase(field), unsafe.staticFieldOffset(field), value);
    }

    private static boolean isWindows(String osName) {
        return osName != null && osName.toLowerCase(Locale.ROOT).contains("win");
    }

    private static void restoreOsName(String originalOsName) {
        if (originalOsName == null) {
            System.clearProperty("os.name");
        } else {
            System.setProperty("os.name", originalOsName);
        }
    }
}
