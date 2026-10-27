/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy_agent;

import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class ByteBuddyAgentInnerAttachmentProviderInnerAccessorInnerSimpleTest {
    @Test
    void createsExternalAttachmentAccessorFromProvidedClassLoader(@TempDir Path temporaryDirectory) {
        File toolsJar = temporaryDirectory.resolve("tools.jar").toFile();
        ByteBuddyAgent.AttachmentProvider.Accessor accessor = ByteBuddyAgent.AttachmentProvider.Accessor.Simple.of(
                ClassLoader.getSystemClassLoader(), toolsJar);

        assertThat(accessor.isAvailable()).isTrue();
        assertThat(accessor.isExternalAttachmentRequired()).isTrue();
        assertThat(accessor.getVirtualMachineType().getName())
                .isEqualTo(ByteBuddyAgent.AttachmentProvider.Accessor.VIRTUAL_MACHINE_TYPE_NAME);
        assertThat(accessor.getExternalAttachment().getVirtualMachineType())
                .isEqualTo(ByteBuddyAgent.AttachmentProvider.Accessor.VIRTUAL_MACHINE_TYPE_NAME);
        assertThat(accessor.getExternalAttachment().getClassPath()).containsExactly(toolsJar);
    }
}
