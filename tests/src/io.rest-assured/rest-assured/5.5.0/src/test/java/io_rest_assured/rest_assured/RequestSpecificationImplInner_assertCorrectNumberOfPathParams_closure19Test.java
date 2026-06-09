/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RequestSpecificationImplInner_assertCorrectNumberOfPathParams_closure19Test {
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal.RequestSpecificationImpl"
            + "$_assertCorrectNumberOfPathParams_closure19";

    @Test
    void validatesRedundantUnnamedPathParametersAlongsideNamedPathParameters() throws Throwable {
        String dynamicallyLoadedClassName = "io.restassured.config.ParamConfig";
        Class<?> resolvedClass = invokeGeneratedClassLookup(dynamicallyLoadedClassName);

        assertThat(resolvedClass.getName()).isEqualTo(dynamicallyLoadedClassName);

        RestAssured.reset();
        try {
            assertThatThrownBy(() -> given()
                    .baseUri("http://127.0.0.1")
                    .port(1)
                    .pathParam("orderId", "named-order")
                    .when()
                    .get("/orders/{orderId}", "overridden-order", "redundant-order"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid number of path parameters")
                    .hasMessageContaining("Expected 1, was 3")
                    .hasMessageContaining("Redundant path parameters are")
                    .hasMessageContaining("overridden-order")
                    .hasMessageContaining("redundant-order");
        } finally {
            RestAssured.reset();
        }
    }

    private static Class<?> invokeGeneratedClassLookup(String className) throws Throwable {
        ClassLoader classLoader = RequestSpecificationImplInner_assertCorrectNumberOfPathParams_closure19Test
                .class
                .getClassLoader();
        Class<?> closureClass = classLoader.loadClass(CLOSURE_CLASS_NAME);
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(closureClass, MethodHandles.lookup());
        MethodHandle classHelper = lookup.findStatic(
                closureClass,
                "class$",
                MethodType.methodType(Class.class, String.class));
        return (Class<?>) classHelper.invokeExact(className);
    }
}
