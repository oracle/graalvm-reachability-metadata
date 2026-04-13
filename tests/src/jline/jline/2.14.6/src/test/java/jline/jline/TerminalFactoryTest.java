/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jline.jline;

import jline.AnsiWindowsTerminal;
import jline.OSvTerminal;
import jline.Terminal;
import jline.TerminalFactory;
import jline.UnixTerminal;
import jline.UnsupportedTerminal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TerminalFactoryTest {

    private String previousTerminalType;

    private ClassLoader previousContextClassLoader;

    @BeforeEach
    void setUp() {
        this.previousTerminalType = System.getProperty(TerminalFactory.JLINE_TERMINAL);
        this.previousContextClassLoader = Thread.currentThread().getContextClassLoader();
        TestConfiguredTerminal.resetState();
        TestFlavorTerminal.resetState();
        NoStringConstructorTerminal.resetState();
        TerminalFactory.reset();
    }

    @AfterEach
    void tearDown() {
        if (this.previousTerminalType == null) {
            System.clearProperty(TerminalFactory.JLINE_TERMINAL);
        } else {
            System.setProperty(TerminalFactory.JLINE_TERMINAL, this.previousTerminalType);
        }
        Thread.currentThread().setContextClassLoader(this.previousContextClassLoader);
        TerminalFactory.registerFlavor(TerminalFactory.Flavor.WINDOWS, AnsiWindowsTerminal.class);
        TerminalFactory.registerFlavor(TerminalFactory.Flavor.UNIX, UnixTerminal.class);
        TerminalFactory.registerFlavor(TerminalFactory.Flavor.OSV, OSvTerminal.class);
        TerminalFactory.reset();
        TestConfiguredTerminal.resetState();
        TestFlavorTerminal.resetState();
        NoStringConstructorTerminal.resetState();
    }

    @Test
    void createLoadsConfiguredTerminalFromContextClassLoader() throws Exception {
        final ClassLoader parentClassLoader = this.previousContextClassLoader != null
            ? this.previousContextClassLoader
            : TerminalFactoryTest.class.getClassLoader();
        final RecordingClassLoader recordingClassLoader = new RecordingClassLoader(
            parentClassLoader,
            TestConfiguredTerminal.class.getName()
        );
        Thread.currentThread().setContextClassLoader(recordingClassLoader);
        TerminalFactory.configure(TestConfiguredTerminal.class.getName());

        final Terminal terminal = TerminalFactory.create();
        try {
            assertInstanceOf(TestConfiguredTerminal.class, terminal);
            assertEquals(1, TestConfiguredTerminal.constructorCalls);
            assertTrue(recordingClassLoader.loadedConfiguredTerminalClass);
        } finally {
            terminal.restore();
        }
    }

    @Test
    void getFlavorUsesBothNoArgAndTtyConstructors() throws Exception {
        TerminalFactory.registerFlavor(TerminalFactory.Flavor.UNIX, TestFlavorTerminal.class);

        final Terminal defaultTerminal = TerminalFactory.getFlavor(TerminalFactory.Flavor.UNIX);
        assertInstanceOf(TestFlavorTerminal.class, defaultTerminal);
        assertEquals(1, TestFlavorTerminal.noArgConstructorCalls);
        assertEquals(0, TestFlavorTerminal.stringConstructorCalls);

        final String ttyDevice = "/dev/pts/test-terminal";
        final Terminal ttyTerminal = TerminalFactory.getFlavor(TerminalFactory.Flavor.UNIX, ttyDevice);
        assertInstanceOf(TestFlavorTerminal.class, ttyTerminal);
        assertEquals(1, TestFlavorTerminal.noArgConstructorCalls);
        assertEquals(1, TestFlavorTerminal.stringConstructorCalls);
        assertEquals(ttyDevice, TestFlavorTerminal.lastTtyDevice);
    }

    @Test
    void getFlavorWithTtyDevicePropagatesNoSuchMethodExceptionBeforeNullConstructorFallback() {
        TerminalFactory.registerFlavor(TerminalFactory.Flavor.UNIX, NoStringConstructorTerminal.class);

        // `Class#getConstructor(String.class)` either returns a constructor or throws.
        // It never returns null, so TerminalFactory's fallback at line 202 is not reachable
        // through the public API on a regular JVM.
        assertThrows(
            NoSuchMethodException.class,
            () -> TerminalFactory.getFlavor(TerminalFactory.Flavor.UNIX, "/dev/pts/missing-constructor")
        );
        assertEquals(0, NoStringConstructorTerminal.constructorCalls);
    }

    private static final class RecordingClassLoader extends ClassLoader {

        private final String configuredTerminalClassName;

        private boolean loadedConfiguredTerminalClass;

        private RecordingClassLoader(final ClassLoader parent, final String configuredTerminalClassName) {
            super(parent);
            this.configuredTerminalClassName = configuredTerminalClassName;
        }

        @Override
        public Class<?> loadClass(final String name) throws ClassNotFoundException {
            if (this.configuredTerminalClassName.equals(name)) {
                this.loadedConfiguredTerminalClass = true;
            }
            return super.loadClass(name);
        }
    }

    public static final class TestConfiguredTerminal extends UnsupportedTerminal {

        private static int constructorCalls;

        public TestConfiguredTerminal() {
            constructorCalls++;
        }

        private static void resetState() {
            constructorCalls = 0;
        }
    }

    public static final class TestFlavorTerminal extends UnsupportedTerminal {

        private static int noArgConstructorCalls;

        private static int stringConstructorCalls;

        private static String lastTtyDevice;

        public TestFlavorTerminal() {
            noArgConstructorCalls++;
        }

        public TestFlavorTerminal(final String ttyDevice) {
            stringConstructorCalls++;
            lastTtyDevice = ttyDevice;
        }

        private static void resetState() {
            noArgConstructorCalls = 0;
            stringConstructorCalls = 0;
            lastTtyDevice = null;
        }
    }

    public static final class NoStringConstructorTerminal extends UnsupportedTerminal {

        private static int constructorCalls;

        public NoStringConstructorTerminal() {
            constructorCalls++;
        }

        private static void resetState() {
            constructorCalls = 0;
        }
    }
}
