/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.internal.MapCreator;
import io.restassured.specification.Argument;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collections;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MapCreatorTest {
    @Test
    void resolvesCompilerGeneratedClassResolver() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                MapCreator.class,
                MethodHandles.lookup());
        MethodHandle classResolver = lookup.findStatic(
                MapCreator.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        Class<?> resolvedClass = (Class<?>) classResolver.invokeExact("io.restassured.internal.MapCreator");

        assertSame(MapCreator.class, resolvedClass);
    }

    @Test
    void includesExpectedArgumentTypeWhenSubsequentArgumentListIsInvalid() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> MapCreator.createMapFromObjects(
                        MapCreator.CollisionStrategy.MERGE,
                        "items.find { it.id == %s }.name",
                        Collections.singletonList(Argument.withArg(1)),
                        equalTo("first"),
                        "items.find { it.id == %s }.name",
                        Collections.singletonList("not a rest-assured argument"),
                        equalTo("second")));

        assertTrue(exception.getMessage().contains("a list of " + Argument.class.getName() + " is required."));
    }
}
