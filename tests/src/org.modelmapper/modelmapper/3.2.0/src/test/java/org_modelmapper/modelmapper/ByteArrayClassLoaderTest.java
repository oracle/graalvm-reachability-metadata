/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.ByteBuddy;
import org.modelmapper.internal.bytebuddy.dynamic.DynamicType;
import org.modelmapper.internal.bytebuddy.dynamic.loading.ByteArrayClassLoader;
import org.modelmapper.internal.bytebuddy.dynamic.scaffold.TypeValidation;
import org.modelmapper.internal.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;

public class ByteArrayClassLoaderTest {

    @Test
    void definesGeneratedTypesThroughPublicInjectionApi() throws ClassNotFoundException {
        String typeName = "org_modelmapper.modelmapper.generated.ByteArrayInjectedType";
        DynamicType.Unloaded<?> unloadedType = makeUnloadedType(typeName);
        ByteArrayClassLoader classLoader = new ByteArrayClassLoader(
            isolatedParent(),
            false,
            Collections.emptyMap());

        Map<String, Class<?>> definedTypes = classLoader.defineClasses(
            Collections.singletonMap(typeName, unloadedType.getBytes()));
        Class<?> definedType = definedTypes.get(typeName);
        Class<?> resolvedType = classLoader.loadClass(typeName);

        assertThat(definedType.getName()).isEqualTo(typeName);
        assertThat(definedType.getClassLoader()).isSameAs(classLoader);
        assertThat(resolvedType).isSameAs(definedType);
    }

    private static DynamicType.Unloaded<?> makeUnloadedType(String typeName) {
        return new ByteBuddy()
            .with(TypeValidation.DISABLED)
            .subclass(Object.class, ConstructorStrategy.Default.NO_CONSTRUCTORS)
            .name(typeName)
            .make();
    }

    private static ClassLoader isolatedParent() {
        return new IsolatedParentClassLoader(ByteArrayClassLoaderTest.class.getClassLoader());
    }

    private static final class IsolatedParentClassLoader extends ClassLoader {
        IsolatedParentClassLoader(ClassLoader parent) {
            super(parent);
        }
    }
}
