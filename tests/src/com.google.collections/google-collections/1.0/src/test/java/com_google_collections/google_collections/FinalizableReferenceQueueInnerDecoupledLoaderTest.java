/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_collections.google_collections;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.base.FinalizableReferenceQueue;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class FinalizableReferenceQueueInnerDecoupledLoaderTest {
    @Test
    void finalizableReferenceQueueUsesDecoupledLoaderWhenSystemLoaderCannotLoadFinalizer() {
        try {
            FinalizableReferenceQueue queue = new FinalizableReferenceQueue();

            assertThat(queue).isNotNull();
            if (ClassLoader.getSystemClassLoader() instanceof FinalizerHidingSystemClassLoader) {
                assertThat(FinalizerHidingSystemClassLoader.rejectedFinalizerLookup()).isTrue();
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }
}
