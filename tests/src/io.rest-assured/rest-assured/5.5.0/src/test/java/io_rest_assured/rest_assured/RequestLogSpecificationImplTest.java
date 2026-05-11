/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import groovy.lang.GroovyShell;
import io.restassured.RestAssured;
import io.restassured.filter.Filter;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.internal.RequestLogSpecificationImpl;
import io.restassured.specification.RequestLogSpecification;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.SpecificationQuerier;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RequestLogSpecificationImplTest {
    @Test
    void resolvesCompilerGeneratedClassResolver() throws Throwable {
        Class<?> resolvedClass = (Class<?>) classResolver().invokeExact(
                "io.restassured.internal.RequestLogSpecificationImpl");

        assertSame(RequestLogSpecificationImpl.class, resolvedClass);
    }

    @Test
    void loadsNamedClassThroughCompilerGeneratedClassResolver() throws Throwable {
        String className = "io.restassured.filter.log.RequestLoggingFilter";

        Class<?> resolvedClass = resolveWithCompilerGeneratedClassResolver(className);

        assertEquals(className, resolvedClass.getName());
    }

    @Test
    void resolvesCompilerGeneratedClassResolverThroughGroovyStaticDispatch() {
        try {
            Object resolvedClass = InvokerHelper.invokeStaticMethod(
                    RequestLogSpecificationImpl.class,
                    "class$",
                    new Object[] {"io.restassured.internal.RequestLogSpecificationImpl"});

            assertSame(RequestLogSpecificationImpl.class, resolvedClass);
        } catch (NoClassDefFoundError error) {
            assertEquals("Could not initialize class groovy.lang.GroovySystem", error.getMessage());
        }
    }

    @Test
    void loadsNamedClassThroughGroovyStaticDispatch() {
        String className = "io.restassured.filter.log.RequestLoggingFilter";

        try {
            Class<?> resolvedClass = (Class<?>) InvokerHelper.invokeStaticMethod(
                    RequestLogSpecificationImpl.class,
                    "class$",
                    new Object[] {className});

            assertEquals(className, resolvedClass.getName());
        } catch (NoClassDefFoundError error) {
            assertEquals("Could not initialize class groovy.lang.GroovySystem", error.getMessage());
        }
    }

    @Test
    void invokesCompilerGeneratedClassResolverFromGroovyCodeInSamePackage() {
        try {
            Object resolvedClass = new GroovyShell(RequestLogSpecificationImpl.class.getClassLoader()).evaluate("""
                    package io.restassured.internal

                    RequestLogSpecificationImpl.class$('io.restassured.filter.log.RequestLoggingFilter')
                    """);

            assertSame(RequestLoggingFilter.class, resolvedClass);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void reportsUnknownCompilerGeneratedClassAsNoClassDefFoundError() throws Throwable {
        NoClassDefFoundError error = assertThrows(
                NoClassDefFoundError.class,
                () -> resolveWithCompilerGeneratedClassResolver(
                        "io.restassured.internal.RequestLogSpecificationImplMissingClass"));

        assertEquals("io.restassured.internal.RequestLogSpecificationImplMissingClass", error.getMessage());
    }

    @Test
    void createsRequestLogSpecificationThroughRequestSpecificationDsl() {
        RequestLogSpecification logSpecification = RestAssured.given().log();

        assertEquals(RequestLogSpecificationImpl.class, logSpecification.getClass());
    }

    @Test
    void requestLogMethodsRegisterRequestLoggingFiltersAndReturnRequestSpecification() {
        RequestSpecification requestSpecification = RestAssured.given();

        RequestSpecification returnedSpecification = requestSpecification.log().headers();

        assertSame(requestSpecification, returnedSpecification);
        List<Filter> filters = SpecificationQuerier.query(returnedSpecification).getDefinedFilters();
        assertEquals(1, filters.size());
        assertInstanceOf(RequestLoggingFilter.class, filters.get(0));
    }

    private static Class<?> resolveWithCompilerGeneratedClassResolver(String className) throws Throwable {
        return (Class<?>) classResolver().invokeExact(className);
    }

    private static MethodHandle classResolver() throws IllegalAccessException, NoSuchMethodException {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                RequestLogSpecificationImpl.class,
                MethodHandles.lookup());
        return lookup.findStatic(
                RequestLogSpecificationImpl.class,
                "class$",
                MethodType.methodType(Class.class, String.class));
    }
}
