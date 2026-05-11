/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import groovy.lang.Closure;
import io.restassured.internal.MapCreator;
import io.restassured.specification.Argument;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MapCreatorInner_isRestAssuredArguments_closure1Test {
    private static final String CLOSURE_CLASS_NAME =
            "io.restassured.internal.MapCreator$_isRestAssuredArguments_closure1";

    @Test
    void createMapFromObjectsRecognizesRestAssuredArgumentLists() {
        Argument argument = Argument.withArg(42);

        Map<String, Object> map = MapCreator.createMapFromObjects(
                MapCreator.CollisionStrategy.OVERWRITE,
                "items.find { it.id == %s }.name",
                List.of(argument),
                equalTo("book"));

        assertTrue(map.containsKey("items.find { it.id == %s }.name"));
    }

    @Test
    void closureAcceptsArgumentInstances() throws Exception {
        Closure<?> closure = newClosure();

        Object result = closure.call(Argument.withArg("isbn"));

        assertEquals(Boolean.TRUE, result);
    }

    @Test
    void closureRejectsNonArgumentInstances() throws Exception {
        Closure<?> closure = newClosure();

        Object result = closure.call("isbn");

        assertEquals(Boolean.FALSE, result);
    }

    @Test
    void noArgClosureDispatchTreatsMissingValueAsNonArgument() throws Exception {
        Closure<?> closure = newClosure();

        Object result = closure.call();

        assertEquals(Boolean.FALSE, result);
    }

    @Test
    void resolvesCompilerGeneratedArgumentClassReference() throws Throwable {
        MethodHandle classResolver = classResolver();

        Class<?> resolvedClass = (Class<?>) classResolver.invokeExact(argumentClassName());

        assertSame(Argument.class, resolvedClass);
    }

    @Test
    void resolvesCompilerGeneratedArgumentClassReferenceWithJavaMethodDispatch() throws Exception {
        Method classResolver = closureClass().getDeclaredMethod("class$", String.class);
        classResolver.setAccessible(true);

        Object resolvedClass = classResolver.invoke(null, argumentClassName());

        assertSame(Argument.class, resolvedClass);
    }

    @Test
    void compilerGeneratedClassResolverReportsMissingClass() throws Exception {
        Method classResolver = closureClass().getDeclaredMethod("class$", String.class);
        classResolver.setAccessible(true);

        InvocationTargetException exception = assertThrows(
                InvocationTargetException.class,
                () -> classResolver.invoke(null, "io.restassured.specification.DoesNotExist"));

        assertInstanceOf(NoClassDefFoundError.class, exception.getCause());
    }

    private static Closure<?> newClosure() throws Exception {
        Constructor<?> constructor = closureClass().getDeclaredConstructor(Object.class, Object.class);
        constructor.setAccessible(true);
        return (Closure<?>) constructor.newInstance(MapCreator.class, MapCreator.class);
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

    private static String argumentClassName() {
        return String.join(".", "io", "restassured", "specification", "Argument");
    }
}
