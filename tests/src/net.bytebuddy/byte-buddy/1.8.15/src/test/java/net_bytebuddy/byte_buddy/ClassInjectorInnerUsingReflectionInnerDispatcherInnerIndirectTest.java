/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.dynamic.loading.PackageDefinitionStrategy;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassInjectorInnerUsingReflectionInnerDispatcherInnerIndirectTest {
    @Test
    void injectsGeneratedTypeIntoCustomClassLoader() throws Exception {
        try {
            boolean reflectionInjectionAvailable = ClassInjector.UsingReflection.isAvailable();
            if (reflectionInjectionAvailable) {
                injectGeneratedTypeIntoCustomClassLoader();
            } else {
                assertThat(reflectionInjectionAvailable).isFalse();
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static void injectGeneratedTypeIntoCustomClassLoader() {
        String packageName = "net.bytebuddy.generated.indirect" + System.nanoTime();
        String typeName = packageName + ".ReflectionInjectedType";
        DynamicType.Unloaded<?> unloaded = new ByteBuddy(ClassFileVersion.JAVA_V8)
                .subclass(Object.class)
                .name(typeName)
                .make();
        Map<TypeDescription, byte[]> types = new LinkedHashMap<TypeDescription, byte[]>();
        types.put(unloaded.getTypeDescription(), unloaded.getBytes());

        InjectionClassLoader classLoader = new InjectionClassLoader();
        ClassInjector injector = new ClassInjector.UsingReflection(
                classLoader,
                null,
                PackageDefinitionStrategy.Trivial.INSTANCE,
                false);
        Map<TypeDescription, Class<?>> loadedTypes = injector.inject(types);

        Class<?> loadedType = loadedTypes.get(unloaded.getTypeDescription());
        assertThat(loadedType).isNotNull();
        assertThat(loadedType.getName()).isEqualTo(typeName);
        assertThat(loadedType.getClassLoader()).isSameAs(classLoader);
        assertThat(loadedType.getPackage().getName()).isEqualTo(packageName);
    }

    private static class InjectionClassLoader extends ClassLoader {
        InjectionClassLoader() {
            super(Thread.currentThread().getContextClassLoader());
        }
    }
}
