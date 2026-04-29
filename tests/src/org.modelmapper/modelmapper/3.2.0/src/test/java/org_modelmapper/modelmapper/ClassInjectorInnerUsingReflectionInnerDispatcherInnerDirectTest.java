/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.ByteBuddy;
import org.modelmapper.internal.bytebuddy.dynamic.DynamicType;
import org.modelmapper.internal.bytebuddy.dynamic.loading.UnsafeOverrideDispatcherAccess;
import org.modelmapper.internal.bytebuddy.dynamic.scaffold.TypeValidation;
import org.modelmapper.internal.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;

public class ClassInjectorInnerUsingReflectionInnerDispatcherInnerDirectTest {

    @Test
    void directDispatcherInvokesClassLoaderAndSecurityManagerMethods() throws Exception {
        UnsafeOverrideDispatcherAccess.Operations dispatcher = UnsafeOverrideDispatcherAccess.createDirect();
        ClassLoader targetClassLoader = new IsolatedClassLoader(getClass().getClassLoader());
        String packageName = "org_modelmapper.modelmapper.direct";
        String typeName = packageName + ".DirectReflectionDispatcherDefinedType";
        byte[] binaryRepresentation = makeUnloadedType(typeName).getBytes();

        assertThat(dispatcher.findClass(targetClassLoader, typeName)).isNull();
        assertThat(dispatcher.getDefinedPackage(targetClassLoader, packageName)).isNull();
        assertThat(dispatcher.getPackage(targetClassLoader, packageName)).isNull();

        Package definedPackage = dispatcher.definePackage(
            targetClassLoader,
            packageName,
            "ModelMapper Direct Reflection Dispatcher",
            "1",
            "modelmapper-test",
            "modelmapper-test",
            "1",
            "modelmapper-test",
            (URL) null);
        assertThat(definedPackage.getName()).isEqualTo(packageName);
        assertThat(definedPackage.getSpecificationTitle())
            .isEqualTo("ModelMapper Direct Reflection Dispatcher");

        Class<?> definedType = dispatcher.defineClass(
            targetClassLoader,
            typeName,
            binaryRepresentation,
            getClass().getProtectionDomain());

        assertThat(definedType.getName()).isEqualTo(typeName);
        assertThat(definedType.getClassLoader()).isSameAs(targetClassLoader);
        assertThat(dispatcher.findClass(targetClassLoader, typeName)).isSameAs(definedType);
        assertThat(dispatcher.getDefinedPackage(targetClassLoader, packageName)).isSameAs(definedPackage);
        assertThat(dispatcher.getPackage(targetClassLoader, packageName)).isSameAs(definedPackage);
    }

    private static DynamicType.Unloaded<?> makeUnloadedType(String typeName) {
        return new ByteBuddy()
            .with(TypeValidation.DISABLED)
            .subclass(Object.class, ConstructorStrategy.Default.NO_CONSTRUCTORS)
            .name(typeName)
            .make();
    }

    private static final class IsolatedClassLoader extends ClassLoader {
        IsolatedClassLoader(ClassLoader parent) {
            super(parent);
        }
    }
}
