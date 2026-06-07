/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.dynamic.Nexus;
import net.bytebuddy.dynamic.NexusAccessor;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

import static org.assertj.core.api.Assertions.assertThat;

public class NexusAccessorInnerDispatcherInnerAvailableTest {
    @Test
    void cleansReferenceThroughAvailableDispatcher() {
        String previousDisabledProperty = System.getProperty(Nexus.PROPERTY);
        System.clearProperty(Nexus.PROPERTY);
        try {
            assertThat(NexusAccessor.isAlive()).isTrue();

            ReferenceQueue<ClassLoader> referenceQueue = new ReferenceQueue<ClassLoader>();
            Reference<ClassLoader> reference = new WeakReference<ClassLoader>(
                    Thread.currentThread().getContextClassLoader(),
                    referenceQueue);

            NexusAccessor.clean(reference);
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
}
