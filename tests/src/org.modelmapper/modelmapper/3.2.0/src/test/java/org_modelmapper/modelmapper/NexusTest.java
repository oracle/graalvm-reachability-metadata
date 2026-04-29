/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.dynamic.Nexus;
import org.modelmapper.internal.bytebuddy.implementation.LoadedTypeInitializer;

public class NexusTest {

    @Test
    void invokesRegisteredLoadedTypeInitializerWhenTypeIsInitialized() throws Exception {
        RecordingLoadedTypeInitializer initializer = new RecordingLoadedTypeInitializer();
        int identification = System.identityHashCode(initializer);

        Nexus.register(
            String.class.getName(),
            String.class.getClassLoader(),
            null,
            identification,
            initializer);
        Nexus.initialize(String.class, identification);

        assertThat(initializer.loadedType()).isSameAs(String.class);
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

}
