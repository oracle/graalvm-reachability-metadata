/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy_agent;

import net.bytebuddy.agent.Attacher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AttacherTest {
    @BeforeEach
    void resetVirtualMachine() {
        RecordingVirtualMachine.reset();
    }

    @Test
    void mainLoadsJavaAgentAndCombinesArguments() {
        Attacher.main(new String[] {
                RecordingVirtualMachine.class.getName(),
                "1234",
                "agent.jar",
                "false",
                "=first",
                "second",
                "third"
        });

        assertThat(RecordingVirtualMachine.attachedProcessId).isEqualTo("1234");
        assertThat(RecordingVirtualMachine.loadedAgent).isEqualTo("agent.jar");
        assertThat(RecordingVirtualMachine.loadedAgentArgument).isEqualTo("first second third");
        assertThat(RecordingVirtualMachine.loadedNativeAgent).isNull();
        assertThat(RecordingVirtualMachine.detached).isTrue();
    }

    @Test
    void mainLoadsNativeAgentWithNullArgument() {
        Attacher.main(new String[] {
                RecordingVirtualMachine.class.getName(),
                "5678",
                "native-agent.so",
                "true",
                ""
        });

        assertThat(RecordingVirtualMachine.attachedProcessId).isEqualTo("5678");
        assertThat(RecordingVirtualMachine.loadedNativeAgent).isEqualTo("native-agent.so");
        assertThat(RecordingVirtualMachine.loadedNativeAgentArgument).isNull();
        assertThat(RecordingVirtualMachine.loadedAgent).isNull();
        assertThat(RecordingVirtualMachine.detached).isTrue();
    }

    public static class RecordingVirtualMachine {
        private static String attachedProcessId;
        private static String loadedAgent;
        private static String loadedAgentArgument;
        private static String loadedNativeAgent;
        private static String loadedNativeAgentArgument;
        private static boolean detached;

        public static RecordingVirtualMachine attach(String processId) {
            attachedProcessId = processId;
            return new RecordingVirtualMachine();
        }

        public void loadAgent(String agent, String argument) {
            loadedAgent = agent;
            loadedAgentArgument = argument;
        }

        public void loadAgentPath(String agent, String argument) {
            loadedNativeAgent = agent;
            loadedNativeAgentArgument = argument;
        }

        public void detach() {
            detached = true;
        }

        private static void reset() {
            attachedProcessId = null;
            loadedAgent = null;
            loadedAgentArgument = null;
            loadedNativeAgent = null;
            loadedNativeAgentArgument = null;
            detached = false;
        }
    }
}
