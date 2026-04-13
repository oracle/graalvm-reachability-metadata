/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jline.console.internal;

import jline.TerminalFactory;
import jline.console.completer.Completer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class ConsoleRunnerTest {

    private static final String COMPLETERS_PROPERTY = ConsoleRunner.class.getName() + ".completers";

    private static final String HISTORY_NAME = "test-history";

    private String previousUserHome;

    private String previousTerminalType;

    private String previousHistoryName;

    private String previousCompleters;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        this.previousUserHome = System.getProperty("user.home");
        this.previousTerminalType = System.getProperty(TerminalFactory.JLINE_TERMINAL);
        this.previousHistoryName = System.getProperty(ConsoleRunner.property);
        this.previousCompleters = System.getProperty(COMPLETERS_PROPERTY);
        TargetMain.resetState();
        RecordingCompleter.resetState();
        TerminalFactory.reset();
    }

    @AfterEach
    void tearDown() {
        restoreProperty("user.home", this.previousUserHome);
        restoreProperty(TerminalFactory.JLINE_TERMINAL, this.previousTerminalType);
        restoreProperty(ConsoleRunner.property, this.previousHistoryName);
        restoreProperty(COMPLETERS_PROPERTY, this.previousCompleters);
        TerminalFactory.reset();
        TargetMain.resetState();
        RecordingCompleter.resetState();
    }

    @Test
    void mainLoadsConfiguredCompleterAndInvokesTargetMainMethod() throws Exception {
        System.setProperty("user.home", this.tempDir.toString());
        System.setProperty(TerminalFactory.JLINE_TERMINAL, TerminalFactory.NONE);
        System.setProperty(ConsoleRunner.property, HISTORY_NAME);
        System.setProperty(COMPLETERS_PROPERTY, RecordingCompleter.class.getName());

        final String[] forwardedArgs = {"first", "second"};
        final String[] runnerArgs = {TargetMain.class.getName(), forwardedArgs[0], forwardedArgs[1]};

        ConsoleRunner.main(runnerArgs);

        assertEquals(1, RecordingCompleter.constructorCalls);
        assertEquals(1, TargetMain.invocationCount);
        assertArrayEquals(forwardedArgs, TargetMain.lastArgs);

        final Path historyFile = this.tempDir.resolve(
            ".jline-" + TargetMain.class.getName() + "." + HISTORY_NAME + ".history"
        );
        assertTrue(historyFile.toFile().isFile());
    }

    private static void restoreProperty(final String name, final String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    public static final class RecordingCompleter implements Completer {

        private static int constructorCalls;

        public RecordingCompleter() {
            constructorCalls++;
        }

        @Override
        public int complete(final String buffer, final int cursor, final List<CharSequence> candidates) {
            return -1;
        }

        private static void resetState() {
            constructorCalls = 0;
        }
    }

    public static final class TargetMain {

        private static int invocationCount;

        private static String[] lastArgs = new String[0];

        public static void main(final String[] args) {
            invocationCount++;
            lastArgs = Arrays.copyOf(args, args.length);
        }

        private static void resetState() {
            invocationCount = 0;
            lastArgs = new String[0];
        }
    }
}
