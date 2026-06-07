/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ByteArrayClassLoader;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ByteArrayClassLoaderTest {
    private static final String GENERATED_TYPE_NAME = "net_bytebuddy.byte_buddy.generated.ByteArrayLoadedType";

    @Test
    void injectsDefinitionsIntoUnsealedClassLoader() throws Exception {
        try {
            DynamicType.Unloaded<?> unloaded = new ByteBuddy(ClassFileVersion.JAVA_V8)
                    .subclass(Object.class)
                    .name(GENERATED_TYPE_NAME)
                    .make();
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
            assertThat(loadedType.getClassLoader()).isSameAs(classLoader);
            assertThat(classLoader.loadClass(typeName)).isSameAs(loadedType);
            assertThat(classLoader.getResource(typeName.replace('.', '/') + ".class")).isNull();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }
}
