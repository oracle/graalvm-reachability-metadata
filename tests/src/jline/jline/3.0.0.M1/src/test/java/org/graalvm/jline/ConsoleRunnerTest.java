/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import jline.TerminalFactory;
import jline.console.completer.Completer;
import jline.console.internal.ConsoleRunner;
import jline.internal.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ConsoleRunnerTest {

    private String originalCompleters;
    private String originalHistoryName;
    private String originalTerminalType;
    private String originalUserHome;
    private InputStream originalSystemIn;

    @TempDir
    Path temporaryHome;

    @BeforeEach
    void setUp() {
        originalCompleters = System.getProperty(ConsoleRunner.class.getName() + ".completers");
        originalHistoryName = System.getProperty(ConsoleRunner.property);
        originalTerminalType = System.getProperty(TerminalFactory.JLINE_TERMINAL);
        originalUserHome = System.getProperty("user.home");
        originalSystemIn = System.in;

        TrackingCompleter.constructorCalls = 0;
        TargetApplication.invocationCount = 0;
        TargetApplication.lastArguments = null;
        TargetApplication.systemInClassName = null;

        System.setProperty(ConsoleRunner.class.getName() + ".completers", TrackingCompleter.class.getName());
        System.setProperty(ConsoleRunner.property, "console-runner");
        System.setProperty(TerminalFactory.JLINE_TERMINAL, TerminalFactory.NONE);
        System.setProperty("user.home", temporaryHome.toString());
        System.setIn(new ByteArrayInputStream(new byte[0]));

        Configuration.reset();
        TerminalFactory.reset();
    }

    @AfterEach
    void tearDown() {
        restoreProperty(ConsoleRunner.class.getName() + ".completers", originalCompleters);
        restoreProperty(ConsoleRunner.property, originalHistoryName);
        restoreProperty(TerminalFactory.JLINE_TERMINAL, originalTerminalType);
        restoreProperty("user.home", originalUserHome);
        System.setIn(originalSystemIn);

        Configuration.reset();
        TerminalFactory.reset();
    }

    @Test
    void mainLoadsConfiguredCompleterAndInvokesTheTargetMainMethod() throws Exception {
        ConsoleRunner.main(new String[]{TargetApplication.class.getName(), "alpha", "beta"});

        assertThat(TrackingCompleter.constructorCalls).isEqualTo(1);
        assertThat(TargetApplication.invocationCount).isEqualTo(1);
        assertThat(TargetApplication.lastArguments).containsExactly("alpha", "beta");
        assertThat(TargetApplication.systemInClassName).contains("ConsoleReaderInputStream");
        assertThat(System.in.getClass().getName()).doesNotContain("ConsoleReaderInputStream");
        assertThat(temporaryHome.resolve(".jline-" + TargetApplication.class.getName() + ".console-runner.history"))
                .exists();
    }

    private static void restoreProperty(final String name, final String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    public static final class TrackingCompleter implements Completer {

        private static int constructorCalls;

        public TrackingCompleter() {
            constructorCalls++;
        }

        @Override
        public int complete(final String buffer, final int cursor, final List<CharSequence> candidates) {
            candidates.add("candidate");
            return 0;
        }
    }

    public static final class TargetApplication {

        private static int invocationCount;
        private static String[] lastArguments;
        private static String systemInClassName;

        public static void main(final String[] args) {
            invocationCount++;
            lastArguments = args.clone();
            systemInClassName = System.in.getClass().getName();
        }
    }
}
