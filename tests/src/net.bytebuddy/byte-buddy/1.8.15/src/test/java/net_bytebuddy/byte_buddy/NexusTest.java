/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.LoadedTypeInitializer;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NexusTest {
    @Test
    void initializesRegisteredLoadedTypeInitializerFromGeneratedTypeInitializer() throws Exception {
        RecordingLoadedTypeInitializer initializer = new RecordingLoadedTypeInitializer();
        try {
            DynamicType.Loaded<?> loadedType = NexusInitializationSupport.makeGeneratedType(initializer)
                    .load(
                            NexusTest.class.getClassLoader(),
                            ClassLoadingStrategy.Default.WRAPPER.allowExistingTypes());

            Class<?> generatedType = loadedType.getLoaded();
            Class<?> initializedType = Class.forName(generatedType.getName(), true, generatedType.getClassLoader());

            assertThat(initializedType).isSameAs(generatedType);
            assertThat(initializer.getLoadedType()).isSameAs(generatedType);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    public static class RecordingLoadedTypeInitializer implements LoadedTypeInitializer {
        private Class<?> loadedType;

        @Override
        public void onLoad(Class<?> type) {
            loadedType = type;
        }

        @Override
        public boolean isAlive() {
            return true;
        }

        Class<?> getLoadedType() {
            return loadedType;
        }
    }
}
