/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3;

import org.apache.pekko.protobufv3.internal.GeneratedMessageLite;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

public final class GeneratedMessageLiteMethodLookup {
    private GeneratedMessageLiteMethodLookup() {
    }

    public static String lookupDefaultInstanceMethodName() {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                    GeneratedMessageLite.class,
                    MethodHandles.lookup());
            MethodHandle methodHandle = lookup.findStatic(
                    GeneratedMessageLite.class,
                    "getMethodOrDie",
                    MethodType.methodType(Method.class, Class.class, String.class, Class[].class));
            Class<?>[] parameters = new Class<?>[0];
            Method method = (Method) methodHandle.invokeExact(
                    (Class<?>) UnregisteredLiteMessage.class,
                    "getDefaultInstance",
                    parameters);
            return method.getName();
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("Unable to call GeneratedMessageLite method lookup", e);
        }
    }
}
