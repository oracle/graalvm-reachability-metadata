/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.internal.MapCreator;
import io.restassured.internal.MapCreator.ArgsAndValue;
import io.restassured.specification.Argument;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MapCreatorInnerArgsAndValueTest {
    @Test
    void storesRestAssuredArgumentsAndMatcherValue() {
        Argument firstArgument = Argument.withArg("sku-1");
        Matcher<String> valueMatcher = equalTo("book");

        Map<String, Object> map = MapCreator.createMapFromObjects(
                MapCreator.CollisionStrategy.OVERWRITE,
                "items.find { it.sku == %s }.name",
                List.of(firstArgument),
                valueMatcher);

        Object value = map.get("items.find { it.sku == %s }.name");

        ArgsAndValue argsAndValue = assertInstanceOf(ArgsAndValue.class, value);
        assertEquals(List.of(firstArgument), argsAndValue.getArgs());
        assertSame(valueMatcher, argsAndValue.getValue());
    }

    @Test
    void resolvesCompilerGeneratedClassResolver() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                ArgsAndValue.class,
                MethodHandles.lookup());
        MethodHandle classResolver = lookup.findStatic(
                ArgsAndValue.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        Object resolvedClass = classResolver.invokeWithArguments("io.restassured.internal.MapCreator$ArgsAndValue");

        assertSame(ArgsAndValue.class, resolvedClass);
    }

    @Test
    void groovyRuntimeInvokesCompilerGeneratedClassResolver() {
        Object resolvedClass = InvokerHelper.invokeStaticMethod(
                ArgsAndValue.class,
                "class$",
                new Object[] {"io.restassured.internal.MapCreator$ArgsAndValue"});

        assertSame(ArgsAndValue.class, resolvedClass);
    }

    @Test
    void compilerGeneratedClassResolverConvertsMissingClassToNoClassDefFoundError() throws Exception {
        Method classResolver = ArgsAndValue.class.getDeclaredMethod("class$", String.class);
        classResolver.setAccessible(true);

        InvocationTargetException exception = assertThrows(
                InvocationTargetException.class,
                () -> classResolver.invoke(null, "io.restassured.internal.DoesNotExist"));

        assertInstanceOf(NoClassDefFoundError.class, exception.getCause());
    }
}
