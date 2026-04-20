/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import jline.AnsiWindowsTerminal;
import jline.OSvTerminal;
import jline.Terminal;
import jline.TerminalFactory;
import jline.TerminalSupport;
import jline.UnixTerminal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TerminalFactoryTest {

    private String originalTerminalType;
    private ClassLoader originalContextClassLoader;

    @BeforeEach
    void setUp() {
        originalTerminalType = System.getProperty(TerminalFactory.JLINE_TERMINAL);
        originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        TerminalFactory.reset();
        restoreDefaultFlavors();
    }

    @AfterEach
    void tearDown() {
        if (originalTerminalType == null) {
            System.clearProperty(TerminalFactory.JLINE_TERMINAL);
        } else {
            System.setProperty(TerminalFactory.JLINE_TERMINAL, originalTerminalType);
        }
        Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        TerminalFactory.reset();
        restoreDefaultFlavors();
    }

    @Test
    void createLoadsConfiguredTerminalThroughTheContextClassLoader() {
        TrackingClassLoader trackingClassLoader = new TrackingClassLoader(TerminalFactoryTest.class.getClassLoader());
        Thread.currentThread().setContextClassLoader(trackingClassLoader);
        TerminalFactory.configure(ClassLoaderTerminal.class.getName());

        Terminal terminal = TerminalFactory.create();

        assertThat(trackingClassLoader.loadedClassNames).contains(ClassLoaderTerminal.class.getName());
        assertThat(terminal).isInstanceOf(ClassLoaderTerminal.class);
        assertThat(((ClassLoaderTerminal) terminal).initialized).isTrue();
    }

    @Test
    void getFlavorUsesTheTtyDeviceConstructorWhenATtyDeviceIsProvided() throws Exception {
        TerminalFactory.registerFlavor(TerminalFactory.Flavor.UNIX, TtyDeviceTerminal.class);

        Terminal terminal = TerminalFactory.getFlavor(TerminalFactory.Flavor.UNIX, "/dev/tty-test");

        assertThat(terminal).isInstanceOf(TtyDeviceTerminal.class);
        assertThat(((TtyDeviceTerminal) terminal).ttyDevice).isEqualTo("/dev/tty-test");
    }

    @Test
    void getFlavorUsesTheNoArgConstructorWhenNoTtyDeviceIsProvided() throws Exception {
        TerminalFactory.registerFlavor(TerminalFactory.Flavor.OSV, NoArgTerminal.class);

        Terminal terminal = TerminalFactory.getFlavor(TerminalFactory.Flavor.OSV);

        assertThat(terminal).isInstanceOf(NoArgTerminal.class);
        assertThat(((NoArgTerminal) terminal).constructed).isTrue();
    }

    @Test
    void getFlavorRequiresAPublicTtyDeviceConstructorWhenATtyDeviceIsProvided() {
        TerminalFactory.registerFlavor(TerminalFactory.Flavor.WINDOWS, NoStringConstructorTerminal.class);

        assertThatThrownBy(() -> TerminalFactory.getFlavor(TerminalFactory.Flavor.WINDOWS, "/dev/tty-test"))
                .isInstanceOf(NoSuchMethodException.class);
    }

    private static void restoreDefaultFlavors() {
        TerminalFactory.registerFlavor(TerminalFactory.Flavor.WINDOWS, AnsiWindowsTerminal.class);
        TerminalFactory.registerFlavor(TerminalFactory.Flavor.UNIX, UnixTerminal.class);
        TerminalFactory.registerFlavor(TerminalFactory.Flavor.OSV, OSvTerminal.class);
    }

    public static final class TrackingClassLoader extends ClassLoader {

        private final List<String> loadedClassNames = new ArrayList<String>();

        public TrackingClassLoader(final ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(final String name) throws ClassNotFoundException {
            loadedClassNames.add(name);
            return super.loadClass(name);
        }
    }

    public static final class ClassLoaderTerminal extends TerminalSupport {

        private boolean initialized;

        public ClassLoaderTerminal() {
            super(true);
        }

        @Override
        public void init() {
            initialized = true;
        }
    }

    public static final class TtyDeviceTerminal extends TerminalSupport {

        private final String ttyDevice;

        public TtyDeviceTerminal(final String ttyDevice) {
            super(true);
            this.ttyDevice = ttyDevice;
        }
    }

    public static final class NoArgTerminal extends TerminalSupport {

        private final boolean constructed;

        public NoArgTerminal() {
            super(true);
            constructed = true;
        }
    }

    public static final class NoStringConstructorTerminal extends TerminalSupport {

        public NoStringConstructorTerminal() {
            super(true);
        }
    }
}
