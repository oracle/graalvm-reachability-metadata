/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package lombok.launch;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AgentTest {
    @Test
    void premainLoadsAgentLauncherBeforeRejectingMissingInstrumentation() {
        assertThatThrownBy(() -> Agent.premain("test-args", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void agentmainLoadsAgentLauncherBeforeRejectingMissingInstrumentation() {
        assertThatThrownBy(() -> Agent.agentmain("test-args", null))
                .isInstanceOf(NullPointerException.class);
    }
}
