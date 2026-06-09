/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import io.restassured.internal.MapCreator.CollisionStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class MapCreatorInnerCollisionStrategyTest {
    @Test
    void generatedGroovyEnumClassHelperResolvesLibraryClassName() throws Throwable {
        assertEquals(CollisionStrategy.MERGE, CollisionStrategy.valueOf("MERGE"));
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(CollisionStrategy.class, MethodHandles.lookup());
        MethodHandle classHelper = lookup.findStatic(
                CollisionStrategy.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        Class<?> resolvedClass = (Class<?>) classHelper.invokeExact(CollisionStrategy.class.getName());

        assertSame(CollisionStrategy.class, resolvedClass);
    }
}
