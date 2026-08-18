/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;

import sun.misc.Unsafe;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaShadedComGoogleProtobufUnsafeUtilInnerAndroid64MemoryAccessorTest {

    @Test
    void readsStaticObjectFieldThroughAndroid64Accessor() throws Throwable {
        Class<?> accessorClass = Class.forName(
                "org.apache.kafka.shaded.com.google.protobuf.UnsafeUtil$Android64MemoryAccessor");
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(accessorClass, MethodHandles.lookup());
        MethodHandle constructor = lookup.findConstructor(
                accessorClass,
                MethodType.methodType(void.class, Unsafe.class));
        MethodHandle getStaticObject = lookup.findVirtual(
                accessorClass,
                "getStaticObject",
                MethodType.methodType(Object.class, Field.class));
        Object accessor = constructor.invokeWithArguments((Unsafe) null);
        Field field = Boolean.class.getField("TRUE");

        Object value = getStaticObject.invokeWithArguments(accessor, field);

        assertThat(value).isSameAs(Boolean.TRUE);
    }
}
