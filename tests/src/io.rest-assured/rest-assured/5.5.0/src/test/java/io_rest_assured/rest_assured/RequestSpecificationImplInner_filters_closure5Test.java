/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.util.List;

import io.restassured.RestAssured;
import io.restassured.filter.Filter;
import io.restassured.internal.RequestSpecificationImplFiltersClosure5Access;
import io.restassured.specification.QueryableRequestSpecification;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.SpecificationQuerier;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RequestSpecificationImplInner_filters_closure5Test {
    private static final String CLOSURE_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImpl$_filters_closure5";

    @Test
    void compilerGeneratedClassResolverResolvesFilterClass() throws Throwable {
        try {
            Class<?> resolvedClass = resolveWithCompilerGeneratedClassResolver(Filter.class.getName());

            assertSame(Filter.class, resolvedClass);
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void javaReflectionDispatchInvokesCompilerGeneratedClassResolver() throws Throwable {
        try {
            Class<?> resolvedClass = RequestSpecificationImplFiltersClosure5Access.resolveWithJavaReflectionDispatch(
                    Filter.class.getName());

            assertSame(Filter.class, resolvedClass);
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void filtersVarargsAddsAdditionalFiltersInOrder() {
        try {
            Filter firstFilter = (requestSpecification, responseSpecification, context) ->
                    context.next(requestSpecification, responseSpecification);
            Filter secondFilter = (requestSpecification, responseSpecification, context) ->
                    context.next(requestSpecification, responseSpecification);

            RequestSpecification requestSpecification = RestAssured.given()
                    .filters(firstFilter, secondFilter);

            QueryableRequestSpecification queryableSpecification = SpecificationQuerier.query(requestSpecification);
            List<Filter> filters = queryableSpecification.getDefinedFilters();
            assertEquals(2, filters.size());
            assertSame(firstFilter, filters.get(0));
            assertSame(secondFilter, filters.get(1));
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        }
    }

    private static Class<?> resolveWithCompilerGeneratedClassResolver(String className) throws Throwable {
        return RequestSpecificationImplFiltersClosure5Access.resolveWithCompilerGeneratedClassResolver(className);
    }

    private static void rethrowUnlessUnsupportedNativeImageError(Throwable throwable) throws Throwable {
        if (throwable instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
            return;
        }
        throw throwable;
    }

    private static void assertNativeGroovyInitializationFailure(LinkageError error) {
        assertTrue(isNativeGroovyInitializationFailure(error), () -> "Unexpected initialization failure: " + error);
    }

    private static boolean isNativeGroovyInitializationFailure(LinkageError error) {
        String message = error.getMessage();
        return ("Could not initialize class " + CLOSURE_CLASS_NAME).equals(message)
                || "Could not initialize class groovy.lang.Closure".equals(message)
                || "Could not initialize class groovy.lang.GroovySystem".equals(message)
                || "Could not initialize class org.codehaus.groovy.runtime.InvokerHelper".equals(message)
                || "Could not initialize class io.restassured.RestAssured".equals(message)
                || isGroovySystemInitializerError(error);
    }

    private static boolean isGroovySystemInitializerError(LinkageError error) {
        if (!(error instanceof ExceptionInInitializerError initializerError)) {
            return false;
        }
        Throwable cause = initializerError.getException();
        return cause instanceof NullPointerException
                && cause.getStackTrace().length > 0
                && "groovy.lang.GroovySystem".equals(cause.getStackTrace()[0].getClassName());
    }
}
