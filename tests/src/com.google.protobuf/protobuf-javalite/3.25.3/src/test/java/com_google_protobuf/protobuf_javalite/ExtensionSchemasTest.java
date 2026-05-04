/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_javalite;

import static java.lang.invoke.MethodType.methodType;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import org.junit.jupiter.api.Test;

public class ExtensionSchemasTest {
    private static final String MESSAGE_OPTIONS_CLASS_NAME =
            "com.google.protobuf.DescriptorProtos$MessageOptions";
    private static final String PROTOBUF_CLASS_NAME = "com.google.protobuf.Protobuf";
    private static final String SCHEMA_CLASS_NAME = "com.google.protobuf.Schema";

    @Test
    void createsFullRuntimeExtensionSchemaForExtendableGeneratedMessage() throws Throwable {
        Class<?> messageOptionsClass = Class.forName(MESSAGE_OPTIONS_CLASS_NAME);
        Class<?> protobufClass = Class.forName(
                PROTOBUF_CLASS_NAME,
                true,
                messageOptionsClass.getClassLoader()
        );
        Class<?> schemaClass = Class.forName(
                SCHEMA_CLASS_NAME,
                true,
                protobufClass.getClassLoader()
        );
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                protobufClass,
                MethodHandles.lookup()
        );
        MethodHandle getInstance = lookup.findStatic(
                protobufClass,
                "getInstance",
                methodType(protobufClass)
        );
        MethodHandle schemaFor = lookup.findVirtual(
                protobufClass,
                "schemaFor",
                methodType(schemaClass, Class.class)
        );

        Object protobuf = getInstance.invoke();
        Object schema = schemaFor.invoke(protobuf, messageOptionsClass);

        assertThat(schema).isNotNull();
    }
}
