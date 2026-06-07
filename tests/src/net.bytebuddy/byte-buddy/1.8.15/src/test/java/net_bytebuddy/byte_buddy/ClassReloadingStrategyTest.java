/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassReloadingStrategyTest {
    @Test
    void createsReloadingStrategiesFromInstalledByteBuddyAgent() {
        try {
            ClassReloadingStrategy inferredStrategy = ClassReloadingStrategy.fromInstalledAgent();
            assertThat(inferredStrategy).isNotNull();

            ClassReloadingStrategy explicitStrategy = ClassReloadingStrategy.fromInstalledAgent(
                    ClassReloadingStrategy.Strategy.RETRANSFORMATION);
            assertThat(explicitStrategy).isNotNull();
        } catch (IllegalStateException exception) {
            assertThat(exception).hasMessageContaining("The Byte Buddy agent is not installed or not accessible");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }
}
