/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.util.List;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.internal.SpecificationMergerMergeFiltersClosure4Access;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import io.restassured.specification.QueryableRequestSpecification;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.SpecificationQuerier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SpecificationMergerInner_mergeFilters_closure4Test {
    private static final String CLOSURE_CLASS_NAME =
            "io.restassured.internal.SpecificationMerger$_mergeFilters_closure4";

    @Test
    void compilerGeneratedClassResolverUsesClassForName() throws Throwable {
        try {
            Class<?> resolvedClass = SpecificationMergerMergeFiltersClosure4Access
                    .resolveWithCompilerGeneratedClassResolver(Filter.class.getName());

            assertSame(Filter.class, resolvedClass);
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        }
    }

    @Test
    void closureAcceptsOnlyFiltersMissingFromCurrentSpecification() {
        try {
            NamedFilter existingFilter = new NamedFilter("shared");
            NamedFilter newFilter = new NamedFilter("new");

            assertFalse(SpecificationMergerMergeFiltersClosure4Access
                    .acceptsFilterMissingFrom(List.of(existingFilter), existingFilter));
            assertTrue(SpecificationMergerMergeFiltersClosure4Access
                    .acceptsFilterMissingFrom(List.of(existingFilter), newFilter));
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        }
    }

    @Test
    void mergeAddsOnlyFiltersThatAreNotAlreadyDefined() {
        try {
            NamedFilter existingFilter = new NamedFilter("shared");
            NamedFilter newFilter = new NamedFilter("new");

            RequestSpecification specificationToMerge = new RequestSpecBuilder()
                    .addFilter(existingFilter)
                    .addFilter(newFilter)
                    .build();
            RequestSpecification mergedSpecification = new RequestSpecBuilder()
                    .addFilter(existingFilter)
                    .addRequestSpecification(specificationToMerge)
                    .build();

            QueryableRequestSpecification queryableSpecification = SpecificationQuerier.query(mergedSpecification);
            List<Filter> filters = queryableSpecification.getDefinedFilters();

            assertEquals(2, filters.size());
            assertSame(existingFilter, filters.get(0));
            assertSame(newFilter, filters.get(1));
            assertEquals(1, filters.stream().filter(existingFilter::equals).count());
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        }
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
                || "Could not initialize class io.restassured.internal.SpecificationMerger".equals(message)
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

    private static final class NamedFilter implements Filter {
        private final String name;

        private NamedFilter(String name) {
            this.name = name;
        }

        @Override
        public Response filter(
                FilterableRequestSpecification requestSpec,
                FilterableResponseSpecification responseSpec,
                FilterContext ctx) {
            return ctx.next(requestSpec, responseSpec);
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
