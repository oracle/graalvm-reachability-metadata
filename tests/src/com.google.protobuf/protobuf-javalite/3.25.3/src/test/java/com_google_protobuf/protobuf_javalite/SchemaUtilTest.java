/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_javalite;

import static java.lang.invoke.MethodType.methodType;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Struct;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import org.junit.jupiter.api.Test;

public class SchemaUtilTest {
    private static final String SCHEMA_UTIL_CLASS_NAME = "com.google.protobuf.SchemaUtil";

    @Test
    void locatesGeneratedMapDefaultEntryHolder() throws Throwable {
        MethodHandle getMapDefaultEntry = schemaUtilLookup().findStatic(
                schemaUtilClass(),
                "getMapDefaultEntry",
                methodType(Object.class, Class.class, String.class)
        );

        try {
            Object defaultEntry = getMapDefaultEntry.invoke(Struct.class, "fields");

            assertThat(defaultEntry.getClass().getName()).contains("MapEntry");
        } catch (RuntimeException e) {
            assertThat(e)
                    .hasCauseInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Unable to look up map field default entry holder");
        }
    }

    @Test
    void initializesFullUnknownFieldSchemaWhenFullRuntimeIsAvailable() throws Throwable {
        Class<?> schemaUtilClass = schemaUtilClass();
        Class<?> unknownFieldSchemaClass = Class.forName(
                "com.google.protobuf.UnknownFieldSchema",
                false,
                schemaUtilClass.getClassLoader()
        );
        MethodHandle unknownFieldSetFullSchema = schemaUtilLookup().findStatic(
                schemaUtilClass,
                "unknownFieldSetFullSchema",
                methodType(unknownFieldSchemaClass)
        );

        Object fullSchema = unknownFieldSetFullSchema.invoke();

        assertThat(fullSchema).isNotNull();
        assertThat(fullSchema.getClass().getName())
                .isEqualTo("com.google.protobuf.UnknownFieldSetSchema");
    }

    private static MethodHandles.Lookup schemaUtilLookup() throws ClassNotFoundException,
            IllegalAccessException {
        return MethodHandles.privateLookupIn(schemaUtilClass(), MethodHandles.lookup());
    }

    private static Class<?> schemaUtilClass() throws ClassNotFoundException {
        return Class.forName(SCHEMA_UTIL_CLASS_NAME, true, Struct.class.getClassLoader());
    }
}
