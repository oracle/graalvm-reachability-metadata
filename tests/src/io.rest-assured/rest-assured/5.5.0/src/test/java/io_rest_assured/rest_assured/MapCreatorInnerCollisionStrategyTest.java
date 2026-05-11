/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.internal.MapCreator;
import io.restassured.internal.MapCreator.CollisionStrategy;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MapCreatorInnerCollisionStrategyTest {
    @Test
    void exposesGroovyEnumRangeHelpers() {
        Object nextAfterMerge = InvokerHelper.invokeMethod(CollisionStrategy.MERGE, "next", new Object[0]);
        Object nextAfterOverwrite = InvokerHelper.invokeMethod(CollisionStrategy.OVERWRITE, "next", new Object[0]);
        Object previousBeforeMerge = InvokerHelper.invokeMethod(CollisionStrategy.MERGE, "previous", new Object[0]);
        Object previousBeforeOverwrite = InvokerHelper.invokeMethod(
                CollisionStrategy.OVERWRITE,
                "previous",
                new Object[0]);

        assertSame(CollisionStrategy.MERGE, CollisionStrategy.MIN_VALUE);
        assertSame(CollisionStrategy.OVERWRITE, CollisionStrategy.MAX_VALUE);
        assertSame(CollisionStrategy.OVERWRITE, nextAfterMerge);
        assertSame(CollisionStrategy.MERGE, nextAfterOverwrite);
        assertSame(CollisionStrategy.OVERWRITE, previousBeforeMerge);
        assertSame(CollisionStrategy.MERGE, previousBeforeOverwrite);
    }

    @Test
    void resolvesEnumConstantsByName() {
        assertSame(CollisionStrategy.MERGE, CollisionStrategy.valueOf("MERGE"));
        assertSame(CollisionStrategy.OVERWRITE, CollisionStrategy.valueOf("OVERWRITE"));
    }

    @Test
    void mergeStrategyCombinesDuplicateKeys() {
        Map<String, Object> map = MapCreator.createMapFromObjects(
                CollisionStrategy.MERGE,
                "name",
                "first",
                "name",
                "second");

        assertEquals(List.of("first", "second"), map.get("name"));
    }

    @Test
    void overwriteStrategyKeepsLatestDuplicateKey() {
        Map<String, Object> map = MapCreator.createMapFromObjects(
                CollisionStrategy.OVERWRITE,
                "name",
                "first",
                "name",
                "second");

        assertEquals("second", map.get("name"));
    }

    @Test
    void resolvesCompilerGeneratedClassResolver() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                CollisionStrategy.class,
                MethodHandles.lookup());
        MethodHandle classResolver = lookup.findStatic(
                CollisionStrategy.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        Object resolvedClass = classResolver.invokeWithArguments(
                "io.restassured.internal.MapCreator$CollisionStrategy");

        assertSame(CollisionStrategy.class, resolvedClass);
    }

    @Test
    void compilerGeneratedClassResolverReportsMissingType() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                CollisionStrategy.class,
                MethodHandles.lookup());
        MethodHandle classResolver = lookup.findStatic(
                CollisionStrategy.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        assertThrows(
                NoClassDefFoundError.class,
                () -> classResolver.invokeWithArguments("io.restassured.internal.DoesNotExist"));
    }

    @Test
    void groovyRuntimeInvokesCompilerGeneratedClassResolver() {
        Object resolvedClass = InvokerHelper.invokeStaticMethod(
                CollisionStrategy.class,
                "class$",
                new Object[] {"io.restassured.internal.MapCreator$CollisionStrategy"});

        assertSame(CollisionStrategy.class, resolvedClass);
    }
}
