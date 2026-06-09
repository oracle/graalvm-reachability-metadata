/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import io.restassured.specification.QueryableRequestSpecification;
import io.restassured.specification.RequestSpecification;
import io.restassured.spi.AuthFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SpecificationMergerInner_mergeFilters_closure3Test {
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal.SpecificationMerger"
            + "$_mergeFilters_closure3";

    @Test
    void mergingRequestSpecificationsReplacesExistingAuthFilters() throws Throwable {
        assertEquals(AuthFilter.class, invokeGeneratedClassLookup(AuthFilter.class.getName()));
        RestAssured.reset();
        try {
            Filter regularFilter = new HeaderFilter("X-Regular", "kept");
            AuthFilter originalAuthFilter = new HeaderAuthFilter("X-Original-Auth", "removed");
            AuthFilter mergedAuthFilter = new HeaderAuthFilter("X-Merged-Auth", "used");
            CapturingFilterList targetFilters = new CapturingFilterList(
                    regularFilter, originalAuthFilter);

            RequestSpecification targetSpecification = RestAssured.given();
            ((GroovyObject) targetSpecification).setProperty("filters", targetFilters);
            RequestSpecification specificationWithAuthFilter = new RequestSpecBuilder()
                    .addFilter(mergedAuthFilter)
                    .build();

            targetSpecification.spec(specificationWithAuthFilter);

            QueryableRequestSpecification queryableSpecification =
                    (QueryableRequestSpecification) targetSpecification;
            assertEquals(List.of(regularFilter, mergedAuthFilter),
                    queryableSpecification.getDefinedFilters());
            assertEquals(AuthFilter.class,
                    invokeGeneratedClassLookup(
                            targetFilters.getRemovalPredicate(), AuthFilter.class.getName()));
        } finally {
            RestAssured.reset();
        }
    }

    private static Class<?> invokeGeneratedClassLookup(String className) throws Throwable {
        Class<?> closureClass = SpecificationMergerInner_mergeFilters_closure3Test.class
                .getClassLoader()
                .loadClass(CLOSURE_CLASS_NAME);
        return invokeGeneratedClassLookup(closureClass, className);
    }

    private static Class<?> invokeGeneratedClassLookup(Closure<?> closure, String className)
            throws Throwable {
        assertEquals(CLOSURE_CLASS_NAME, closure.getClass().getName());
        return invokeGeneratedClassLookup(closure.getClass(), className);
    }

    @SuppressWarnings("unchecked")
    private static Class<?> invokeGeneratedClassLookup(Class<?> closureClass, String className)
            throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                closureClass, MethodHandles.lookup());
        MethodHandle classHelper = lookup.findStatic(
                closureClass,
                "class$",
                MethodType.methodType(Class.class, String.class));
        CallSite callSite = LambdaMetafactory.metafactory(
                lookup,
                "apply",
                MethodType.methodType(Function.class),
                MethodType.methodType(Object.class, Object.class),
                classHelper,
                MethodType.methodType(Class.class, String.class));
        Function<String, Class<?>> generatedClassLookup = (Function<String, Class<?>>) callSite
                .getTarget()
                .invoke();
        return generatedClassLookup.apply(className);
    }

    private static class HeaderFilter implements Filter {
        private final String headerName;
        private final String headerValue;

        private HeaderFilter(String headerName, String headerValue) {
            this.headerName = headerName;
            this.headerValue = headerValue;
        }

        @Override
        public Response filter(FilterableRequestSpecification requestSpecification,
                FilterableResponseSpecification responseSpecification, FilterContext context) {
            requestSpecification.header(headerName, headerValue);
            return context.next(requestSpecification, responseSpecification);
        }
    }

    private static final class HeaderAuthFilter extends HeaderFilter implements AuthFilter {
        private HeaderAuthFilter(String headerName, String headerValue) {
            super(headerName, headerValue);
        }
    }

    public static final class CapturingFilterList extends ArrayList<Filter> {
        private transient Closure<?> removalPredicate;

        CapturingFilterList(Filter... filters) {
            super(Arrays.asList(filters));
        }

        public boolean removeAll(Closure<?> predicate) {
            removalPredicate = predicate;
            return removeIf(filter -> Boolean.TRUE.equals(predicate.call(filter)));
        }

        Closure<?> getRemovalPredicate() {
            return removalPredicate;
        }
    }
}
