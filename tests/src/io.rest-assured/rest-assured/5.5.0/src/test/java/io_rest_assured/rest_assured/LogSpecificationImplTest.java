/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.RestAssured;
import io.restassured.config.LogConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.internal.LogSpecificationImpl;
import io.restassured.specification.RequestSpecification;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LogSpecificationImplTest {
    @Test
    void resolvesCompilerGeneratedClassResolver() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                LogSpecificationImpl.class,
                MethodHandles.lookup());
        MethodHandle classResolver = lookup.findStatic(
                LogSpecificationImpl.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        Class<?> resolvedClass = (Class<?>) classResolver.invokeExact(
                "io.restassured.internal.LogSpecificationImpl");

        assertSame(LogSpecificationImpl.class, resolvedClass);
    }

    @Test
    void loadsNamedClassThroughCompilerGeneratedClassResolver() throws Throwable {
        String className = "io.restassured.internal.NoParameterValue";

        Class<?> resolvedClass = resolveWithCompilerGeneratedClassResolver(className);

        assertEquals(className, resolvedClass.getName());
    }

    @Test
    void resolvesCompilerGeneratedClassResolverThroughGroovyStaticDispatch() {
        try {
            Object resolvedClass = InvokerHelper.invokeStaticMethod(
                    LogSpecificationImpl.class,
                    "class$",
                    new Object[] {"io.restassured.internal.LogSpecificationImpl"});

            assertSame(LogSpecificationImpl.class, resolvedClass);
        } catch (NoClassDefFoundError error) {
            assertEquals("Could not initialize class groovy.lang.GroovySystem", error.getMessage());
        }
    }

    @Test
    void loadsNamedClassThroughGroovyStaticDispatch() {
        String className = "io.restassured.internal.NoParameterValue";

        try {
            Class<?> resolvedClass = (Class<?>) InvokerHelper.invokeStaticMethod(
                    LogSpecificationImpl.class,
                    "class$",
                    new Object[] {className});

            assertEquals(className, resolvedClass.getName());
        } catch (NoClassDefFoundError error) {
            assertEquals("Could not initialize class groovy.lang.GroovySystem", error.getMessage());
        }
    }

    @Test
    void reportsUnknownCompilerGeneratedClassAsNoClassDefFoundError() throws Throwable {
        NoClassDefFoundError error = assertThrows(
                NoClassDefFoundError.class,
                () -> resolveWithCompilerGeneratedClassResolver(
                        "io.restassured.internal.LogSpecificationImplMissingClass"));

        assertEquals("io.restassured.internal.LogSpecificationImplMissingClass", error.getMessage());
    }

    @Test
    void usesConfiguredDefaultPrintStream() {
        ByteArrayOutputStream logBuffer = new ByteArrayOutputStream();
        PrintStream logStream = new PrintStream(logBuffer, true, StandardCharsets.UTF_8);
        RequestSpecification requestSpecification = RestAssured.given()
                .config(RestAssuredConfig.config().logConfig(LogConfig.logConfig().defaultStream(logStream)));

        PrintStream printStream = new LogSpecificationImpl().getPrintStream(requestSpecification);

        assertSame(logStream, printStream);
    }

    @Test
    void readsPrettyPrintingAndUrlEncodingSettingsFromRequestConfig() {
        RequestSpecification requestSpecification = RestAssured.given()
                .config(RestAssuredConfig.config().logConfig(LogConfig.logConfig()
                        .enablePrettyPrinting(false)
                        .urlEncodeRequestUri(false)));
        LogSpecificationImpl logSpecification = new LogSpecificationImpl();

        assertFalse(logSpecification.shouldPrettyPrint(requestSpecification));
        assertFalse(logSpecification.shouldUrlEncodeRequestUri(requestSpecification));
    }

    @Test
    void defaultsPrettyPrintingAndUrlEncodingForStandardRequestSpecification() {
        RequestSpecification requestSpecification = RestAssured.given();
        LogSpecificationImpl logSpecification = new LogSpecificationImpl();

        assertTrue(logSpecification.shouldPrettyPrint(requestSpecification));
        assertTrue(logSpecification.shouldUrlEncodeRequestUri(requestSpecification));
    }

    private static Class<?> resolveWithCompilerGeneratedClassResolver(String className) throws Throwable {
        return (Class<?>) classResolver().invokeExact(className);
    }

    private static MethodHandle classResolver() throws IllegalAccessException, NoSuchMethodException {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                LogSpecificationImpl.class,
                MethodHandles.lookup());
        return lookup.findStatic(
                LogSpecificationImpl.class,
                "class$",
                MethodType.methodType(Class.class, String.class));
    }
}
