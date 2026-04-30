/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package antlr.antlr;

import antlr.build.Tool;
import antlr.build.ToolDynamicAccessSupport;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class AntlrBuildToolTest {
    @Test
    void loadsToolClassThroughBuildToolBridge() {
        Class<?> toolClass = ToolDynamicAccessSupport.loadToolClassThroughGeneratedBridge();

        assertThat(toolClass).isEqualTo(Tool.class);
    }

    @Test
    void reportsUsageWhenMainReceivesWrongArgumentCount() {
        PrintStream originalErr = System.err;
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        try {
            System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));

            Tool.main(new String[0]);
        } finally {
            System.setErr(originalErr);
        }

        assertThat(err.toString(StandardCharsets.UTF_8)).contains("usage: java antlr.build.Tool action");
    }

    @Test
    void reportsMissingApplicationOrAction() {
        RecordingTool tool = new RecordingTool();

        tool.perform(null, "build");

        assertThat(tool.errorMessage).isEqualTo("missing app or action");
        assertThat(tool.errorCause).isNull();
    }

    @Test
    void reportsUnknownApplicationForUnresolvableBuildApplication() {
        RecordingTool tool = new RecordingTool();

        tool.perform("NoSuchApplication", "build");

        assertThat(tool.errorMessage).isEqualTo("no such application NoSuchApplication");
        assertThat(tool.errorCause).isInstanceOf(ClassNotFoundException.class);
    }

    private static final class RecordingTool extends Tool {
        private String errorMessage;
        private Exception errorCause;

        @Override
        public void error(String msg) {
            errorMessage = msg;
        }

        @Override
        public void error(String msg, Exception e) {
            errorMessage = msg;
            errorCause = e;
        }
    }
}
