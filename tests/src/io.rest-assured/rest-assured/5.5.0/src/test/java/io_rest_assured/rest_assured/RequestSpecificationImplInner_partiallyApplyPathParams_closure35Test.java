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
import java.util.function.Function;

import io.restassured.RestAssured;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.SpecificationQuerier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RequestSpecificationImplInner_partiallyApplyPathParams_closure35Test {
    private static final String REQUEST_SPECIFICATION_IMPL_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImpl";
    private static final String CLOSURE_CLASS_NAME = REQUEST_SPECIFICATION_IMPL_CLASS_NAME
            + "$_partiallyApplyPathParams_closure35";

    @Test
    void generatedClosureClassLookupAndNamedPathParameterApplicationAreAvailable() throws Throwable {
        Class<?> dynamicallyResolvedClass = invokeGeneratedClassLookup(REQUEST_SPECIFICATION_IMPL_CLASS_NAME);
        assertThat(dynamicallyResolvedClass.getName()).isEqualTo(REQUEST_SPECIFICATION_IMPL_CLASS_NAME);

        RestAssured.reset();
        try {
            RequestSpecification specification = RestAssured
                    .given()
                    .baseUri("http://example.com")
                    .basePath("/api")
                    .pathParam("segment", "first");
            ((FilterableRequestSpecification) specification).path("/orders/{segment}");

            String requestUri = SpecificationQuerier.query(specification).getURI();

            assertThat(requestUri).isEqualTo("http://example.com/api/orders/first");
        } finally {
            RestAssured.reset();
        }
    }

    @SuppressWarnings("unchecked")
    private static Class<?> invokeGeneratedClassLookup(String className) throws Throwable {
        Class<?> closureClass = RequestSpecificationImplInner_partiallyApplyPathParams_closure35Test.class
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
}
