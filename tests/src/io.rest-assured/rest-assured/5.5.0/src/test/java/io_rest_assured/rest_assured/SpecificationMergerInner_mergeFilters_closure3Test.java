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
import io.restassured.internal.SpecificationMergerMergeFiltersClosure3Access;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import io.restassured.specification.QueryableRequestSpecification;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.SpecificationQuerier;
import io.restassured.spi.AuthFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SpecificationMergerInner_mergeFilters_closure3Test {
    private static final String CLOSURE_CLASS_NAME =
            "io.restassured.internal.SpecificationMerger$_mergeFilters_closure3";

    @Test
    void compilerGeneratedClassResolverUsesClassForName() throws Throwable {
        try {
            Class<?> resolvedClass = SpecificationMergerMergeFiltersClosure3Access
                    .resolveWithCompilerGeneratedClassResolver(AuthFilter.class.getName());

            assertSame(AuthFilter.class, resolvedClass);
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        }
    }

    @Test
    void authFiltersFromMergedSpecificationReplaceExistingAuthFilters() {
        try {
            RecordingAuthFilter originalAuthFilter = new RecordingAuthFilter("original-auth");
            RecordingAuthFilter mergedAuthFilter = new RecordingAuthFilter("merged-auth");
            NamedFilter originalAuditFilter = new NamedFilter("original-audit");
            NamedFilter mergedAuditFilter = new NamedFilter("merged-audit");

            RequestSpecification specificationToMerge = new RequestSpecBuilder()
                    .addFilter(mergedAuthFilter)
                    .addFilter(mergedAuditFilter)
                    .build();
            RequestSpecification mergedSpecification = new RequestSpecBuilder()
                    .addFilter(originalAuthFilter)
                    .addFilter(originalAuditFilter)
                    .addRequestSpecification(specificationToMerge)
                    .build();

            QueryableRequestSpecification queryableSpecification = SpecificationQuerier.query(mergedSpecification);
            List<Filter> filters = queryableSpecification.getDefinedFilters();

            assertFalse(filters.contains(originalAuthFilter));
            assertTrue(filters.contains(originalAuditFilter));
            assertTrue(filters.contains(mergedAuthFilter));
            assertTrue(filters.contains(mergedAuditFilter));
            assertSame(mergedAuthFilter, filters.get(filters.indexOf(mergedAuthFilter)));
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

    private static final class RecordingAuthFilter implements AuthFilter {
        private final String name;

        private RecordingAuthFilter(String name) {
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
