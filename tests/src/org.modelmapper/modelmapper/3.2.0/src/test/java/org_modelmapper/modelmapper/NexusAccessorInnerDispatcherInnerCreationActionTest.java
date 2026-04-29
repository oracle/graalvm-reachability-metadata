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
import org.modelmapper.internal.bytebuddy.dynamic.NexusAccessor.Dispatcher;
import org.modelmapper.internal.bytebuddy.implementation.LoadedTypeInitializer;

public class NexusAccessorInnerDispatcherInnerCreationActionTest {

    @Test
    void createsNexusDispatcherAndUsesDiscoveredEntryPoints() {
        Dispatcher dispatcher = Dispatcher.CreationAction.INSTANCE.run();
        ReferenceQueue<ClassLoader> referenceQueue = new ReferenceQueue<>();
        ClassLoader classLoader = new IsolatedClassLoader(getClass().getClassLoader());
        RecordingLoadedTypeInitializer initializer = new RecordingLoadedTypeInitializer();

        assertThat(dispatcher.isAlive()).isTrue();

        dispatcher.register(
            String.class.getName(),
            classLoader,
            referenceQueue,
            System.identityHashCode(initializer),
            initializer);
        dispatcher.clean(new WeakReference<>(classLoader, referenceQueue));

        assertThat(initializer.loadedType()).isNull();
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
