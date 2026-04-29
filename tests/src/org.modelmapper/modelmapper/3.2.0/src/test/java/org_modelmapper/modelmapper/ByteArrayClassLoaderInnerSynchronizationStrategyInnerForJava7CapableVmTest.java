/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.ByteBuddy;
import org.modelmapper.internal.bytebuddy.dynamic.DynamicType;
import org.modelmapper.internal.bytebuddy.dynamic.loading.ByteArrayClassLoader;
import org.modelmapper.internal.bytebuddy.dynamic.scaffold.TypeValidation;
import org.modelmapper.internal.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;

public class ByteArrayClassLoaderInnerSynchronizationStrategyInnerForJava7CapableVmTest {

    @Test
    void definesGeneratedTypesThroughSynchronizedClassLoadingLocks() throws ClassNotFoundException {
        String firstTypeName = "org_modelmapper.modelmapper.generated.SynchronizedLockFirstType";
        String secondTypeName = "org_modelmapper.modelmapper.generated.SynchronizedLockSecondType";
        Map<String, byte[]> typeDefinitions = new LinkedHashMap<>();
        typeDefinitions.put(firstTypeName, makeUnloadedType(firstTypeName).getBytes());
        typeDefinitions.put(secondTypeName, makeUnloadedType(secondTypeName).getBytes());
        ByteArrayClassLoader classLoader = new ByteArrayClassLoader(
            isolatedParent(),
            false,
            new LinkedHashMap<>());

        Map<String, Class<?>> definedTypes = classLoader.defineClasses(typeDefinitions);

        assertThat(definedTypes.keySet()).containsExactly(firstTypeName, secondTypeName);
        assertThat(definedTypes.get(firstTypeName).getClassLoader()).isSameAs(classLoader);
        assertThat(definedTypes.get(secondTypeName).getClassLoader()).isSameAs(classLoader);
        assertThat(classLoader.loadClass(firstTypeName)).isSameAs(definedTypes.get(firstTypeName));
        assertThat(classLoader.loadClass(secondTypeName)).isSameAs(definedTypes.get(secondTypeName));
    }

    private static DynamicType.Unloaded<?> makeUnloadedType(String typeName) {
        return new ByteBuddy()
            .with(TypeValidation.DISABLED)
            .subclass(Object.class, ConstructorStrategy.Default.NO_CONSTRUCTORS)
            .name(typeName)
            .make();
    }

    private static ClassLoader isolatedParent() {
        return new IsolatedParentClassLoader(
            ByteArrayClassLoaderInnerSynchronizationStrategyInnerForJava7CapableVmTest.class.getClassLoader());
    }

    private static final class IsolatedParentClassLoader extends ClassLoader {
        IsolatedParentClassLoader(ClassLoader parent) {
            super(parent);
        }
    }
}
