/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy_agent;

import net.bytebuddy.agent.ByteBuddyAgent;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ByteBuddyAgentInnerAttachmentProviderInnerAccessorInnerSimpleTest {
    @Test
    void createsExternalAttachmentAccessorFromProvidedClassLoader(@TempDir Path temporaryDirectory) {
        File toolsJar = temporaryDirectory.resolve("tools.jar").toFile();
        RecordingClassLoader classLoader = new RecordingClassLoader();

        ByteBuddyAgent.AttachmentProvider.Accessor accessor =
                ByteBuddyAgent.AttachmentProvider.Accessor.Simple.of(classLoader, toolsJar);

        assertThat(classLoader.requestedName)
                .isEqualTo(ByteBuddyAgent.AttachmentProvider.Accessor.VIRTUAL_MACHINE_TYPE_NAME);
        assertThat(accessor.isAvailable()).isTrue();
        assertThat(accessor.isExternalAttachmentRequired()).isTrue();
        assertThat(accessor.getVirtualMachineType()).isEqualTo(RecordingVirtualMachine.class);
        assertThat(accessor.getExternalAttachment().getVirtualMachineType())
                .isEqualTo(RecordingVirtualMachine.class.getName());
        assertThat(accessor.getExternalAttachment().getClassPath()).containsExactly(toolsJar);
    }

    @Test
    void createsUnavailableAccessorWhenJ9VirtualMachineClassIsMissing() {
        try {
            ByteBuddyAgent.AttachmentProvider.Accessor accessor =
                    ByteBuddyAgent.AttachmentProvider.Accessor.Simple.ofJ9();

            if (accessor.isAvailable()) {
                assertThat(accessor.getVirtualMachineType().getName())
                        .isEqualTo(ByteBuddyAgent.AttachmentProvider.Accessor.VIRTUAL_MACHINE_TYPE_NAME_J9);
                assertThat(accessor.isExternalAttachmentRequired()).isTrue();
                assertThat(accessor.getExternalAttachment().getClassPath()).isEmpty();
            } else {
                assertThatThrownBy(accessor::getVirtualMachineType)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("unavailable accessor");
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static final class RecordingClassLoader extends ClassLoader {
        private String requestedName;

        private RecordingClassLoader() {
            super(null);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            requestedName = name;
            if (ByteBuddyAgent.AttachmentProvider.Accessor.VIRTUAL_MACHINE_TYPE_NAME.equals(name)) {
                return RecordingVirtualMachine.class;
            }
            throw new ClassNotFoundException(name);
        }
    }

    public static final class RecordingVirtualMachine {
    }
}
