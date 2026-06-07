/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.dynamic.Nexus;
import net.bytebuddy.dynamic.NexusAccessor;
import net.bytebuddy.implementation.LoadedTypeInitializer;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NexusAccessorInnerDispatcherInnerCreationActionTest {
    @Test
    void createsDispatcherForRegisteringAliveInitializer() {
        String previousDisabledProperty = System.getProperty(Nexus.PROPERTY);
        System.clearProperty(Nexus.PROPERTY);
        try {
            assertThat(NexusAccessor.isAlive()).isTrue();

            NexusAccessor nexusAccessor = new NexusAccessor();
            nexusAccessor.register(
                    SampleType.class.getName(),
                    SampleType.class.getClassLoader(),
                    42,
                    new AliveLoadedTypeInitializer());
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } finally {
            if (previousDisabledProperty == null) {
                System.clearProperty(Nexus.PROPERTY);
            } else {
                System.setProperty(Nexus.PROPERTY, previousDisabledProperty);
            }
        }
    }

    public static class AliveLoadedTypeInitializer implements LoadedTypeInitializer {
        @Override
        public void onLoad(Class<?> type) {
            /* no action required */
        }

        @Override
        public boolean isAlive() {
            return true;
        }
    }

    public static class SampleType {
    }
}
