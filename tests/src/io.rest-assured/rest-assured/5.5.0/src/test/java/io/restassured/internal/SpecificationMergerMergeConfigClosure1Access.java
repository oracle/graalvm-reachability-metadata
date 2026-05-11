/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io.restassured.internal;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class SpecificationMergerMergeConfigClosure1Access {
    private SpecificationMergerMergeConfigClosure1Access() {
    }

    public static Class<?> resolveWithCompilerGeneratedClassResolver(String className) throws Throwable {
        return (Class<?>) classResolver().invokeExact(className);
    }

    private static MethodHandle classResolver() throws NoSuchMethodException, IllegalAccessException {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                SpecificationMerger$_mergeConfig_closure1.class,
                MethodHandles.lookup());
        return lookup.findStatic(
                SpecificationMerger$_mergeConfig_closure1.class,
                "class$",
                MethodType.methodType(Class.class, String.class));
    }
}
