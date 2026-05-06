/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy_agent;

import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ByteBuddyAgentTest {
    private static final String ALLOW_SELF_ATTACH = "jdk.attach.allowAttachSelf";
    private static final String JAVA_HOME = "java.home";

    private static String originalAllowSelfAttach;

    @BeforeAll
    static void requireExternalSelfAttachment() {
        originalAllowSelfAttach = System.getProperty(ALLOW_SELF_ATTACH);
        System.setProperty(ALLOW_SELF_ATTACH, Boolean.FALSE.toString());
    }

    @AfterAll
    static void restoreSelfAttachmentProperty() {
        restoreProperty(ALLOW_SELF_ATTACH, originalAllowSelfAttach);
    }

    @Test
    void getInstrumentationLooksUpInstallerViaSystemClassLoader() {
        assertThatThrownBy(ByteBuddyAgent::getInstrumentation)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not initialized");
    }

    @Test
    void externalAttachmentCopiesAttacherClassResource(@TempDir Path temporaryDirectory) throws IOException {
        Path agentJar = Files.createFile(temporaryDirectory.resolve("agent.jar"));
        String originalLatentResolve = System.getProperty(ByteBuddyAgent.LATENT_RESOLVE);
        String originalJavaHome = System.getProperty(JAVA_HOME);
        System.setProperty(ByteBuddyAgent.LATENT_RESOLVE, Boolean.TRUE.toString());
        System.setProperty(JAVA_HOME, temporaryDirectory.resolve("missing-java-home").toString());
        try {
            assertThatThrownBy(() -> ByteBuddyAgent.attach(agentJar.toFile(),
                    ByteBuddyAgent.ProcessProvider.ForCurrentVm.INSTANCE,
                    new AlwaysExternalAttachmentProvider()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Error during attachment");
        } finally {
            restoreProperty(ByteBuddyAgent.LATENT_RESOLVE, originalLatentResolve);
            restoreProperty(JAVA_HOME, originalJavaHome);
        }
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    private static final class AlwaysExternalAttachmentProvider implements ByteBuddyAgent.AttachmentProvider {
        @Override
        public ByteBuddyAgent.AttachmentProvider.Accessor attempt() {
            return new AlwaysExternalAccessor();
        }
    }

    private static final class AlwaysExternalAccessor implements ByteBuddyAgent.AttachmentProvider.Accessor {
        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public boolean isExternalAttachmentRequired() {
            return true;
        }

        @Override
        public Class<?> getVirtualMachineType() {
            return RecordingVirtualMachine.class;
        }

        @Override
        public ExternalAttachment getExternalAttachment() {
            return new ExternalAttachment(RecordingVirtualMachine.class.getName(), Collections.<File>emptyList());
        }
    }

    public static final class RecordingVirtualMachine {
        private static String attachedProcessId;
        private static String loadedAgent;
        private static String loadedAgentArgument;
        private static boolean detached;

        public static RecordingVirtualMachine attach(String processId) {
            attachedProcessId = processId;
            return new RecordingVirtualMachine();
        }

        public void loadAgent(String agent, String argument) {
            loadedAgent = agent;
            loadedAgentArgument = argument;
        }

        public void detach() {
            detached = true;
        }
    }
}
