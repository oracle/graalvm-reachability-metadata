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
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandles;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassInjectorInnerUsingLookupInnerDispatcherInnerForJava9CapableVmTest {
    @Test
    void resolvesLookupScopeAndDefinesGeneratedClass() {
        try {
            ClassInjector.UsingLookup injector = new ExposedUsingLookup(MethodHandles.lookup()).in(Neighbor.class);
            assertThat(injector.lookupType()).isEqualTo(Neighbor.class);

            DynamicType.Unloaded<?> unloaded = new ByteBuddy(ClassFileVersion.JAVA_V8)
                    .subclass(Object.class)
                    .name("net_bytebuddy.byte_buddy.LookupInjected" + Long.toUnsignedString(System.nanoTime()))
                    .make();
            Map<TypeDescription, byte[]> types = new LinkedHashMap<TypeDescription, byte[]>();
            types.put(unloaded.getTypeDescription(), unloaded.getBytes());

            Map<TypeDescription, Class<?>> loadedTypes = injector.inject(types);
            Class<?> loadedType = loadedTypes.get(unloaded.getTypeDescription());

            assertThat(loadedType).isNotNull();
            assertThat(loadedType.getName()).isEqualTo(unloaded.getTypeDescription().getName());
            assertThat(loadedType.getClassLoader()).isSameAs(Neighbor.class.getClassLoader());
            assertThat(loadedType.getPackage().getName()).isEqualTo("net_bytebuddy.byte_buddy");
        } catch (IllegalStateException exception) {
            Throwable cause = exception.getCause();
            if (!(cause instanceof Error) || !NativeImageSupport.isUnsupportedFeatureError((Error) cause)) {
                throw exception;
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static class ExposedUsingLookup extends ClassInjector.UsingLookup {
        ExposedUsingLookup(Object lookup) {
            super(lookup);
        }
    }

    private static class Neighbor {
    }
}
