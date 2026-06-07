/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.dynamic.ClassFileLocator;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassFileLocatorInnerAgentBasedTest {
    @Test
    void locatesLoadedClassFromInstalledByteBuddyAgent() throws Exception {
        try {
            try (ClassFileLocator classFileLocator = ClassFileLocator.AgentBased.fromInstalledAgent(
                    ClassFileLocatorInnerAgentBasedTest.class.getClassLoader())) {
                ClassFileLocator.Resolution resolution = classFileLocator.locate(
                        ClassFileLocatorInnerAgentBasedTest.class.getName());

                assertThat(resolution.isResolved()).isTrue();
                byte[] binaryRepresentation = resolution.resolve();
                assertThat(binaryRepresentation).isNotEmpty();
                assertThat(Arrays.copyOf(binaryRepresentation, 4)).containsExactly(
                        (byte) 0xCA,
                        (byte) 0xFE,
                        (byte) 0xBA,
                        (byte) 0xBE);
            }
        } catch (IllegalStateException exception) {
            assertThat(exception).hasMessageContaining("The Byte Buddy agent is not installed or not accessible");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }
}
