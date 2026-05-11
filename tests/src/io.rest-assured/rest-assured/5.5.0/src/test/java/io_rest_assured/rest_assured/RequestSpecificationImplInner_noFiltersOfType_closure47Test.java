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
import io.restassured.filter.FilterContext;
import io.restassured.internal.RequestSpecificationImplNoFiltersOfTypeClosure47DirectAccess;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import io.restassured.specification.QueryableRequestSpecification;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.SpecificationQuerier;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RequestSpecificationImplInner_noFiltersOfType_closure47Test {
    private static final String CLOSURE_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImpl$_noFiltersOfType_closure47";

    @Test
    void compilerGeneratedClassResolverUsesClassForName() throws Throwable {
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
    void noFiltersOfTypeRemovesFiltersAssignableToTheRequestedType() {
        MarkerFilter markerFilter = new MarkerFilter();
        ChildMarkerFilter childMarkerFilter = new ChildMarkerFilter();
        OtherFilter otherFilter = new OtherFilter();

        try {
            RequestSpecification requestSpecification = RestAssured.given()
                    .filter(markerFilter)
                    .filter(childMarkerFilter)
                    .filter(otherFilter)
                    .noFiltersOfType(MarkerFilter.class);

            QueryableRequestSpecification queryableSpecification = SpecificationQuerier.query(requestSpecification);
            List<Filter> filters = queryableSpecification.getDefinedFilters();
            assertEquals(1, filters.size());
            assertSame(otherFilter, filters.get(0));
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        } finally {
            RestAssured.reset();
        }
    }

    private static Class<?> resolveWithCompilerGeneratedClassResolver(String className) throws Throwable {
        return RequestSpecificationImplNoFiltersOfTypeClosure47DirectAccess
                .resolveWithCompilerGeneratedClassResolver(className);
    }

    private static void rethrowUnlessUnsupportedNativeImageError(Error error) {
        if (NativeImageSupport.isUnsupportedFeatureError(error)) {
            return;
        }
        throw error;
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

    private static class MarkerFilter implements Filter {
        @Override
        public Response filter(
                FilterableRequestSpecification requestSpec,
                FilterableResponseSpecification responseSpec,
                FilterContext context) {
            return context.next(requestSpec, responseSpec);
        }
    }

    private static final class ChildMarkerFilter extends MarkerFilter {
    }

    private static final class OtherFilter implements Filter {
        @Override
        public Response filter(
                FilterableRequestSpecification requestSpec,
                FilterableResponseSpecification responseSpec,
                FilterContext context) {
            return context.next(requestSpec, responseSpec);
        }
    }
}
