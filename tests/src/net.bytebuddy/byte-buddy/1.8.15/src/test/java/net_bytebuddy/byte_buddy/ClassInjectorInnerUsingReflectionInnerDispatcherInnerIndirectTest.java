/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import java.util.Map;

import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.dynamic.loading.PackageDefinitionStrategy;
import net.bytebuddy.utility.JavaType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ClassInjectorInnerUsingReflectionInnerDispatcherInnerIndirectTest {
    @Test
    void reusesAlreadyLoadedTypesWithoutDefiningClasses() {
        TypeDescription type = TypeDescription.ForLoadedType.of(JavaType.class);
        Map<TypeDescription, byte[]> definitions = Map.of(type, ClassFileLocator.ForClassLoader.read(JavaType.class).resolve());
        ClassInjector injector = new ClassInjector.UsingReflection(JavaType.class.getClassLoader());

        Map<TypeDescription, Class<?>> loaded = injector.inject(definitions);

        assertEquals(Map.of(type, JavaType.class), loaded);
        assertSame(JavaType.class, injector.inject(definitions).get(type));
        assertEquals(JavaType.METHOD_TYPE, Enum.valueOf(loaded.get(type).asSubclass(JavaType.class), "METHOD_TYPE"));
    }

    @Test
    void rejectsAlreadyLoadedTypesWhenExistingTypesAreForbidden() {
        TypeDescription type = TypeDescription.ForLoadedType.of(JavaType.class);
        Map<TypeDescription, byte[]> definitions = Map.of(type, ClassFileLocator.ForClassLoader.read(JavaType.class).resolve());
        ClassInjector injector = new ClassInjector.UsingReflection(JavaType.class.getClassLoader(), null,
                PackageDefinitionStrategy.Trivial.INSTANCE, true);

        assertThrows(IllegalStateException.class, () -> injector.inject(definitions));
        assertSame(JavaType.METHOD_TYPE, Enum.valueOf(JavaType.class, "METHOD_TYPE"));
    }
}
