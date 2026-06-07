/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ByteArrayClassLoader;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ByteArrayClassLoaderTest {
    private static final String GENERATED_TYPE_NAME = GeneratedTypeFixtures.BYTE_ARRAY_LOADED_TYPE;

    @Test
    void injectsDefinitionsIntoUnsealedClassLoader() throws Exception {
        try {
            DynamicType.Unloaded<?> unloaded = GeneratedTypeFixtures.byteArrayLoadedType();
            String typeName = unloaded.getTypeDescription().getName();
            ByteArrayClassLoader classLoader = new ByteArrayClassLoader(
                    ByteArrayClassLoaderTest.class.getClassLoader(),
                    false,
                    Collections.<String, byte[]>emptyMap());

            Map<String, Class<?>> loadedTypes = classLoader.defineClasses(
                    Collections.singletonMap(typeName, unloaded.getBytes()));

            Class<?> loadedType = loadedTypes.get(typeName);
            assertThat(loadedType).isNotNull();
            assertThat(loadedType.getName()).isEqualTo(typeName);
            assertThat(classLoader.loadClass(typeName)).isSameAs(loadedType);
            if (isNativeImageRuntime()) {
                assertThat(loadedType.getClassLoader())
                        .isIn(classLoader, ByteArrayClassLoaderTest.class.getClassLoader());
            } else {
                assertThat(loadedType.getClassLoader()).isSameAs(classLoader);
                assertThat(classLoader.getResource(typeName.replace('.', '/') + ".class")).isNull();
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }
}
