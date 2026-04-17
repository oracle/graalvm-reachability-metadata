/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package antlr.build;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolTest {
    @Test
    void performReturnsBeforeActionLookupWhenCreateInstanceOfSucceeds() {
        BuildToolAction.invoked = false;
        RecordingTool tool = new RecordingTool();

        tool.perform("antlr.build.BuildToolAction", "build");

        assertThat(BuildToolAction.invoked).isFalse();
        assertThat(tool.lastErrorMessage).isNull();
    }

    @Test
    void performLoadsFallbackClassForShortNameButStillReturnsWithoutInvokingAction() {
        BuildToolAction.invoked = false;
        RecordingTool tool = new RecordingTool();

        tool.perform("BuildToolAction", "build");

        assertThat(BuildToolAction.invoked).isFalse();
        assertThat(tool.lastErrorMessage).isEqualTo("no such application BuildToolAction");
    }

    @Test
    void performReportsMissingApplicationWhenFallbackClassLoadAlsoFails() {
        RecordingTool tool = new RecordingTool();

        tool.perform("MissingBuildToolAction", "build");

        assertThat(tool.lastErrorMessage).isEqualTo("no such application MissingBuildToolAction");
    }
}

class BuildToolAction {
    static boolean invoked;

    public void build(Tool tool) {
        invoked = true;
    }
}

class RecordingTool extends Tool {
    String lastErrorMessage;

    @Override
    public void error(String msg, Exception e) {
        lastErrorMessage = msg;
    }
}
