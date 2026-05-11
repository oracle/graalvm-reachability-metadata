/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.internal.MapCreator;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MapCreatorInner_createArgumentArray_closure2Test {
    private static final String CLOSURE_CLASS_NAME =
            "io.restassured.internal.MapCreator$_createArgumentArray_closure2";

    @Test
    void createMapFromParamsWithAdditionalParameterPairsReachesClosure() {
        CreateMapFromParameterPairs creator = MapCreator::createMapFromParams;

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> creator.create(
                        MapCreator.CollisionStrategy.OVERWRITE,
                        "first",
                        "one",
                        "second",
                        "two",
                        "third",
                        "three"));

        assertEquals("You must supply at least one key and one value.", exception.getMessage());
    }

    @Test
    void resolvesCompilerGeneratedClassResolver() throws Throwable {
        MethodHandle classResolver = classResolver();

        Class<?> resolvedClass = (Class<?>) classResolver.invokeWithArguments(MapCreator.class.getName());

        assertSame(MapCreator.class, resolvedClass);
    }

    @Test
    void compilerGeneratedClassResolverReportsMissingClass() throws Throwable {
        MethodHandle classResolver = classResolver();

        assertThrows(
                NoClassDefFoundError.class,
                () -> classResolver.invokeWithArguments("io.restassured.internal.DoesNotExist"));
    }

    @Test
    void resolvesCompilerGeneratedClassResolverWithJavaMethodDispatch() throws Throwable {
        Method classResolver = closureClass().getDeclaredMethod("class$", String.class);
        classResolver.setAccessible(true);

        Object resolvedClass = classResolver.invoke(null, MapCreator.class.getName());

        assertSame(MapCreator.class, resolvedClass);
    }

    @Test
    void compilerGeneratedClassResolverReportsMissingClassWithJavaMethodDispatch() throws Throwable {
        Method classResolver = closureClass().getDeclaredMethod("class$", String.class);
        classResolver.setAccessible(true);

        InvocationTargetException exception = assertThrows(
                InvocationTargetException.class,
                () -> classResolver.invoke(null, "io.restassured.internal.DoesNotExist"));

        assertInstanceOf(NoClassDefFoundError.class, exception.getCause());
    }

    @FunctionalInterface
    private interface CreateMapFromParameterPairs {
        Map<String, Object> create(
                MapCreator.CollisionStrategy collisionStrategy,
                String firstParam,
                Object... parameters);
    }

    private static MethodHandle classResolver()
            throws IllegalAccessException, NoSuchMethodException, ClassNotFoundException {
        Class<?> closureClass = closureClass();
        MethodHandles.Lookup closureLookup = MethodHandles.privateLookupIn(
                closureClass,
                MethodHandles.lookup());
        return closureLookup.findStatic(
                closureClass,
                "class$",
                MethodType.methodType(Class.class, String.class));
    }

    private static Class<?> closureClass() throws IllegalAccessException, ClassNotFoundException {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                MapCreator.class,
                MethodHandles.lookup());
        return lookup.findClass(CLOSURE_CLASS_NAME);
    }
}
