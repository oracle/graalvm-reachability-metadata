/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.dynamic.NexusAccessor;
import org.modelmapper.internal.bytebuddy.implementation.LoadedTypeInitializer;

public class NexusAccessorInnerDispatcherInnerCreationActionTest {

    @Test
    void createsNexusDispatcherAndUsesDiscoveredEntryPoints() {
        String previousSafeMode = System.setProperty("org.modelmapper.internal.bytebuddy.safe", "true");
        try {
            ReferenceQueue<ClassLoader> referenceQueue = new ReferenceQueue<>();
            NexusAccessor nexusAccessor = new NexusAccessor(referenceQueue);
            ClassLoader classLoader = new IsolatedClassLoader(getClass().getClassLoader());
            RecordingLoadedTypeInitializer initializer = new RecordingLoadedTypeInitializer();

            assertThat(NexusAccessor.isAlive()).isTrue();

            nexusAccessor.register(
                String.class.getName(),
                classLoader,
                System.identityHashCode(initializer),
                initializer);
            NexusAccessor.clean(new WeakReference<>(classLoader, referenceQueue));

            assertThat(initializer.loadedType()).isNull();
        } finally {
            restoreSafeMode(previousSafeMode);
        }
    }

    private static void restoreSafeMode(String previousSafeMode) {
        if (previousSafeMode == null) {
            System.clearProperty("org.modelmapper.internal.bytebuddy.safe");
        } else {
            System.setProperty("org.modelmapper.internal.bytebuddy.safe", previousSafeMode);
        }
    }

    private static final class RecordingLoadedTypeInitializer implements LoadedTypeInitializer {
        private final AtomicReference<Class<?>> loadedType = new AtomicReference<>();

        @Override
        public void onLoad(Class<?> type) {
            loadedType.set(type);
        }

        @Override
        public boolean isAlive() {
            return true;
        }

        Class<?> loadedType() {
            return loadedType.get();
        }
    }

    private static final class IsolatedClassLoader extends ClassLoader {
        IsolatedClassLoader(ClassLoader parent) {
            super(parent);
        }
    }

}
