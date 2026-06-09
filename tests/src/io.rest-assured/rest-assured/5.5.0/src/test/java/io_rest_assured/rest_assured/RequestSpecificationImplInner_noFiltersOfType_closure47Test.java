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
import java.util.List;
import java.util.function.Function;

import io.restassured.RestAssured;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.specification.SpecificationQuerier.query;
import static org.assertj.core.api.Assertions.assertThat;

public class RequestSpecificationImplInner_noFiltersOfType_closure47Test {
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal.RequestSpecificationImpl"
            + "$_noFiltersOfType_closure47";

    @Test
    void removesOnlyFiltersAssignableToRequestedType() throws Throwable {
        Class<?> dynamicallyResolvedClass = invokeGeneratedClassLookup("io.restassured.internal.RequestSpecificationImpl");
        assertThat(dynamicallyResolvedClass.getName()).isEqualTo("io.restassured.internal.RequestSpecificationImpl");

        RestAssured.reset();
        try {
            RetainedFilter retainedFilter = new RetainedFilter();
            RemovableFilter removableFilter = new RemovableFilter();
            RemovableChildFilter removableChildFilter = new RemovableChildFilter();

            RequestSpecification requestSpecification = given()
                    .filter(retainedFilter)
                    .filter(removableFilter)
                    .filter(removableChildFilter)
                    .noFiltersOfType(RemovableFilter.class);

            List<Filter> definedFilters = query(requestSpecification).getDefinedFilters();
            assertThat(definedFilters).containsExactly(retainedFilter);
        } finally {
            RestAssured.reset();
        }
    }

    @SuppressWarnings("unchecked")
    private static Class<?> invokeGeneratedClassLookup(String className) throws Throwable {
        Class<?> closureClass = RequestSpecificationImplInner_noFiltersOfType_closure47Test.class
                .getClassLoader()
                .loadClass(CLOSURE_CLASS_NAME);
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(closureClass, MethodHandles.lookup());
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

    private static class RetainedFilter implements Filter {
        @Override
        public Response filter(
                FilterableRequestSpecification requestSpec,
                FilterableResponseSpecification responseSpec,
                FilterContext ctx) {
            return ctx.next(requestSpec, responseSpec);
        }
    }

    private static class RemovableFilter implements Filter {
        @Override
        public Response filter(
                FilterableRequestSpecification requestSpec,
                FilterableResponseSpecification responseSpec,
                FilterContext ctx) {
            return ctx.next(requestSpec, responseSpec);
        }
    }

    private static final class RemovableChildFilter extends RemovableFilter {
    }
}
