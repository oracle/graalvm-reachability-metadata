/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_msgpack.msgpack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;

import org.junit.jupiter.api.Test;
import org.msgpack.MessagePack;
import org.msgpack.MessageTypeException;
import org.msgpack.template.Template;
import org.msgpack.template.TemplateRegistry;

public class TemplateRegistryTest {
    @Test
    void looksUpTemplateForGenericReferenceArrayType() throws Exception {
        final TemplateRegistry registry = new TemplateRegistry(null);
        final GenericArrayType stringArrayType = genericArrayType(String.class, String.class + "[]");

        @SuppressWarnings("unchecked")
        final Template<String[]> template = (Template<String[]>) registry.lookup(stringArrayType);

        final MessagePack messagePack = new MessagePack();
        final String[] source = new String[] { "alpha", "beta", "gamma" };
        final byte[] packed = messagePack.write(source, template);

        final String[] unpacked = messagePack.read(packed, template);

        assertThat(unpacked).containsExactly("alpha", "beta", "gamma");
    }

    @Test
    void reportsMissingGenericArrayComponentTypeAfterTryingClassLoaders() {
        final TemplateRegistry registry = new TemplateRegistry(null);
        final String missingTypeName = "org_msgpack.msgpack.NoSuchMessagePackComponent";
        final GenericArrayType missingArrayType = genericArrayType(
                new NamedType("class " + missingTypeName),
                "class " + missingTypeName + "[]");
        final ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();

        Thread.currentThread().setContextClassLoader(TemplateRegistryTest.class.getClassLoader());
        try {
            assertThatThrownBy(() -> registry.lookup(missingArrayType))
                    .isInstanceOf(MessageTypeException.class)
                    .hasMessageContaining("cannot find template of [L" + missingTypeName + ";");
        } finally {
            Thread.currentThread().setContextClassLoader(previousClassLoader);
        }
    }

    private static GenericArrayType genericArrayType(final Type componentType, final String typeName) {
        return new NamedGenericArrayType(componentType, typeName);
    }

    private static final class NamedGenericArrayType implements GenericArrayType {
        private final Type componentType;
        private final String typeName;

        private NamedGenericArrayType(final Type componentType, final String typeName) {
            this.componentType = componentType;
            this.typeName = typeName;
        }

        @Override
        public Type getGenericComponentType() {
            return componentType;
        }

        @Override
        public String toString() {
            return typeName;
        }
    }

    private static final class NamedType implements Type {
        private final String typeName;

        private NamedType(final String typeName) {
            this.typeName = typeName;
        }

        @Override
        public String toString() {
            return typeName;
        }
    }
}
