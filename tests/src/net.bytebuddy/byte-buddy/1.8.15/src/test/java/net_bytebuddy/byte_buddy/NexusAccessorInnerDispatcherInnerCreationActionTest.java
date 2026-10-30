/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

import net.bytebuddy.dynamic.NexusAccessor;
import net.bytebuddy.implementation.LoadedTypeInitializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NexusAccessorInnerDispatcherInnerCreationActionTest {
    @Test
    void registersInitializerWithoutRunningItBeforeTypeInitialization() {
        ReferenceQueue<ClassLoader> queue = new ReferenceQueue<>();
        NexusAccessor accessor = new NexusAccessor(queue);
        PendingInitializer initializer = new PendingInitializer();
        Class<?> type = PendingInitializer.class;

        assertTrue(NexusAccessor.isAlive());
        accessor.register(type.getName(), type.getClassLoader(), 42, initializer);

        assertEquals(1, initializer.livenessChecks);
        assertFalse(initializer.initialized);
        NexusAccessor.clean(new WeakReference<>(type.getClassLoader()));
        assertFalse(initializer.initialized);
    }

    public static class PendingInitializer implements LoadedTypeInitializer {
        private int livenessChecks;
        private boolean initialized;

        @Override
        public void onLoad(Class<?> type) {
            initialized = true;
        }

        @Override
        public boolean isAlive() {
            livenessChecks++;
            return true;
        }
    }
}
