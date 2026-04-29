/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.BridgeClassLoaderFactoryAccess;
import org.modelmapper.internal.bytebuddy.ByteBuddy;
import org.modelmapper.internal.bytebuddy.description.modifier.Visibility;
import org.modelmapper.internal.bytebuddy.dynamic.DynamicType;
import org.modelmapper.internal.bytebuddy.implementation.FieldAccessor;
import org.modelmapper.internal.cglib.BridgeVisibleType;

public class BridgeClassLoaderFactoryInnerBridgeClassLoaderTest {
    private static final String PACKAGE_NAME = "org_modelmapper.modelmapper.bridge";
    private static final String CONTRACT_NAME = PACKAGE_NAME + ".ExternalContract";
    private static final String SOURCE_NAME = PACKAGE_NAME + ".SourceBean";
    private static final String DESTINATION_NAME = PACKAGE_NAME + ".DestinationBean";
    private static final String ADDITIONAL_ONLY_NAME = PACKAGE_NAME + ".AdditionalOnlyType";

    @Test
    void bridgeClassLoaderFindsClassesInInternalAndAdditionalClassSpaces() throws Exception {
        InterfaceClassLoader interfaceClassLoader = new InterfaceClassLoader(Map.of(
            CONTRACT_NAME, makeInterface(CONTRACT_NAME),
            ADDITIONAL_ONLY_NAME, makeBean(ADDITIONAL_ONLY_NAME, null)));
        Class<?> contractType = interfaceClassLoader.loadClass(CONTRACT_NAME);
        TypeClassLoader typeClassLoader = new TypeClassLoader(
            interfaceClassLoader,
            Map.of(
                SOURCE_NAME, makeBean(SOURCE_NAME, contractType),
                DESTINATION_NAME, makeBean(DESTINATION_NAME, null)));
        Class<?> sourceType = typeClassLoader.loadClass(SOURCE_NAME);
        Class<?> destinationType = typeClassLoader.loadClass(DESTINATION_NAME);

        ClassLoader bridgeClassLoader = BridgeClassLoaderFactoryAccess.getClassLoader(sourceType);
        assertThat(BridgeClassLoaderFactoryAccess.getClassLoader(destinationType))
            .isSameAs(bridgeClassLoader);
        assertThat(bridgeClassLoader.getClass().getName())
            .isEqualTo("org.modelmapper.internal.BridgeClassLoaderFactory$BridgeClassLoader");

        String internalBridgeTypeName = BridgeVisibleType.class.getName();
        Class<?> internalBridgeType = bridgeClassLoader.loadClass(internalBridgeTypeName);
        assertThat(internalBridgeType.getName()).isEqualTo(internalBridgeTypeName);
        assertThat(internalBridgeType).isSameAs(BridgeVisibleType.class);

        Class<?> additionalOnlyType = bridgeClassLoader.loadClass(ADDITIONAL_ONLY_NAME);
        assertThat(additionalOnlyType.getName()).isEqualTo(ADDITIONAL_ONLY_NAME);
        assertThat(additionalOnlyType.getClassLoader()).isSameAs(interfaceClassLoader);
    }

    private static byte[] makeInterface(String typeName) {
        return new ByteBuddy()
            .makeInterface()
            .name(typeName)
            .make()
            .getBytes();
    }

    private static byte[] makeBean(String typeName, Class<?> interfaceType) {
        ByteBuddy byteBuddy = new ByteBuddy();
        DynamicType.Builder<?> builder = byteBuddy
            .subclass(Object.class)
            .name(typeName);
        if (interfaceType != null) {
            builder = builder.implement(interfaceType);
        }
        return builder
            .defineField("value", String.class, Visibility.PRIVATE)
            .defineMethod("getValue", String.class, Visibility.PUBLIC)
            .intercept(FieldAccessor.ofField("value"))
            .defineMethod("setValue", void.class, Visibility.PUBLIC)
            .withParameter(String.class)
            .intercept(FieldAccessor.ofField("value"))
            .make()
            .getBytes();
    }

    private static class DefiningClassLoader extends ClassLoader {
        private final Map<String, byte[]> definitions;

        DefiningClassLoader(Map<String, byte[]> definitions) {
            super(null);
            this.definitions = new HashMap<>(definitions);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes = definitions.get(name);
            if (bytes == null) {
                throw new ClassNotFoundException(name);
            }
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    private static final class InterfaceClassLoader extends DefiningClassLoader {
        InterfaceClassLoader(Map<String, byte[]> definitions) {
            super(definitions);
        }
    }

    private static final class TypeClassLoader extends DefiningClassLoader {
        private final ClassLoader interfaceClassLoader;

        TypeClassLoader(ClassLoader interfaceClassLoader, Map<String, byte[]> definitions) {
            super(definitions);
            this.interfaceClassLoader = interfaceClassLoader;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (CONTRACT_NAME.equals(name)) {
                return interfaceClassLoader.loadClass(name);
            }
            return super.findClass(name);
        }
    }
}
