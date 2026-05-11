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
import java.util.Collection;

import groovy.lang.Reference;

public final class SpecificationMergerMergeFiltersClosure4Access {
    private SpecificationMergerMergeFiltersClosure4Access() {
    }

    public static Class<?> resolveWithCompilerGeneratedClassResolver(String className) throws Throwable {
        return (Class<?>) classResolver().invokeExact(className);
    }

    public static boolean acceptsFilterMissingFrom(Collection<?> thisFilters, Object candidate) {
        Object result = new SpecificationMerger$_mergeFilters_closure4(
                SpecificationMerger.class,
                SpecificationMerger.class,
                new Reference<>(thisFilters)).call(candidate);
        return Boolean.TRUE.equals(result);
    }

    private static MethodHandle classResolver() throws NoSuchMethodException, IllegalAccessException {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                SpecificationMerger$_mergeFilters_closure4.class,
                MethodHandles.lookup());
        return lookup.findStatic(
                SpecificationMerger$_mergeFilters_closure4.class,
                "class$",
                MethodType.methodType(Class.class, String.class));
    }
}
