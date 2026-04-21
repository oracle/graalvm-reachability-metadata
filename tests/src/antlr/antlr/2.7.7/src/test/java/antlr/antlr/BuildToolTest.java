/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package antlr.antlr;

import antlr.build.Tool;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class BuildToolTest {

    @Test
    void reportsUsageWhenMainReceivesUnexpectedArgumentCount() throws Exception {
        String err = captureErr(() -> Tool.main(new String[0]));

        assertThat(err).contains("usage: java antlr.build.Tool action");
    }

    @Test
    void mainAcceptsSingleActionArgumentWithoutPrintingUsage() throws Exception {
        String err = captureErr(() -> Tool.main(new String[] {"build"}));

        assertThat(err).doesNotContain("usage: java antlr.build.Tool action");
    }

    @Test
    void reportsMissingAppOrActionWithoutThrowing() throws Exception {
        Tool tool = new Tool();

        String err = captureErr(() -> tool.perform(null, "build"));

        assertThat(err).contains("missing app or action");
    }

    @Test
    void reportsMissingApplicationForShortBuildClassName() throws Exception {
        Tool tool = new Tool();

        String err = captureErr(() -> tool.perform("ANTLR", "build"));

        assertThat(err).contains("no such application ANTLR");
    }

    @Test
    void reportsMissingApplicationForFullyQualifiedBuildClassName() throws Exception {
        Tool tool = new Tool();

        String err = captureErr(() -> tool.perform("antlr.build.DoesNotExist", "build"));

        assertThat(err).contains("no such application antlr.build.DoesNotExist");
    }

    @Test
    void performWithFullyQualifiedBuildClassReturnsWithoutLoggingUsageOrErrors() throws Exception {
        Tool tool = new Tool();

        String err = captureErr(() -> tool.perform("antlr.build.ANTLR", "build"));

        assertThat(err).isEmpty();
    }

    private static String captureErr(CheckedRunnable action) throws Exception {
        PrintStream originalErr = System.err;
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        try {
            action.run();
        } finally {
            System.setErr(originalErr);
        }
        return err.toString(StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }
}
