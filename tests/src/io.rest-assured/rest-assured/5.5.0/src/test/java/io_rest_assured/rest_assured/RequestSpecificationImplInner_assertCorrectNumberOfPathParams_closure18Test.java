/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.lang.reflect.Method;

import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class RequestSpecificationImplInner_assertCorrectNumberOfPathParams_closure18Test {
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal.RequestSpecificationImpl"
            + "$_assertCorrectNumberOfPathParams_closure18";

    @Test
    void rejectsRedundantUnnamedPathParametersBeforeSendingRequest() throws Throwable {
        String dynamicallyLoadedClassName = RestAssured.class.getName();
        Class<?> dynamicallyLoadedClass = invokeGeneratedClassLookup(dynamicallyLoadedClassName);
        assertEquals(RestAssured.class, dynamicallyLoadedClass);

        RestAssured.reset();

        try {
            assertThatThrownBy(() -> given()
                    .baseUri("http://127.0.0.1")
                    .port(1)
                    .when()
                    .get("/orders/{orderId}", "123", "redundant"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid number of path parameters")
                    .hasMessageContaining("Expected 1, was 2")
                    .hasMessageContaining("Redundant path parameters are: redundant");
        } finally {
            RestAssured.reset();
        }
    }

    private static Class<?> invokeGeneratedClassLookup(String className) throws Throwable {
        ClassLoader classLoader = RequestSpecificationImplInner_assertCorrectNumberOfPathParams_closure18Test.class
                .getClassLoader();
        Class<?> closureClass = classLoader.loadClass(CLOSURE_CLASS_NAME);
        Method classHelper = closureClass.getDeclaredMethod("class$", String.class);
        classHelper.setAccessible(true);
        return (Class<?>) classHelper.invoke(null, className);
    }
}
