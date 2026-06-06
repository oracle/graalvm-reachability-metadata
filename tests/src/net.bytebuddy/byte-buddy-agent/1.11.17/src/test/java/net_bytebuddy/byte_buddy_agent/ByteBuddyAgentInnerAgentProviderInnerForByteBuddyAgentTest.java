/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy_agent;

import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.Installer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.util.Collections;
import java.util.jar.JarFile;

import static org.assertj.core.api.Assertions.assertThat;

public class ByteBuddyAgentInnerAgentProviderInnerForByteBuddyAgentTest {
    private static final String PROCESS_ID = "4711";

    private String originalLatentResolve;

    @BeforeEach
    void resetState() {
        originalLatentResolve = System.getProperty(ByteBuddyAgent.LATENT_RESOLVE);
        System.setProperty(ByteBuddyAgent.LATENT_RESOLVE, Boolean.TRUE.toString());
        Installer.premain(null, null);
        RecordingVirtualMachine.reset();
    }

    @AfterEach
    void restoreState() {
        Installer.premain(null, null);
        RecordingVirtualMachine.reset();
        if (originalLatentResolve == null) {
            System.clearProperty(ByteBuddyAgent.LATENT_RESOLVE);
        } else {
            System.setProperty(ByteBuddyAgent.LATENT_RESOLVE, originalLatentResolve);
        }
    }

    @Test
    void installCreatesTemporaryAgentJarFromInstallerClassResource() throws Exception {
        Instrumentation instrumentation = ByteBuddyAgent.install(new RecordingAttachmentProvider(), () -> PROCESS_ID);

        assertThat(instrumentation).isNull();
        assertThat(RecordingVirtualMachine.attachedProcessId).isEqualTo(PROCESS_ID);
        assertThat(RecordingVirtualMachine.loadedAgentArgument).isNull();
        assertThat(RecordingVirtualMachine.detached).isTrue();

        File agentJar = new File(RecordingVirtualMachine.loadedAgent);
        assertThat(agentJar).isFile();
        try (JarFile jarFile = new JarFile(agentJar)) {
            assertThat(jarFile.getEntry(Installer.class.getName().replace('.', '/') + ".class")).isNotNull();
            assertThat(jarFile.getManifest().getMainAttributes().getValue("Agent-Class"))
                    .isEqualTo(Installer.class.getName());
        }
    }

    private static final class RecordingAttachmentProvider implements ByteBuddyAgent.AttachmentProvider {
        @Override
        public ByteBuddyAgent.AttachmentProvider.Accessor attempt() {
            return new RecordingAccessor();
        }
    }

    private static final class RecordingAccessor implements ByteBuddyAgent.AttachmentProvider.Accessor {
        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public boolean isExternalAttachmentRequired() {
            return false;
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

        private static void reset() {
            attachedProcessId = null;
            loadedAgent = null;
            loadedAgentArgument = null;
            detached = false;
        }
    }
}
