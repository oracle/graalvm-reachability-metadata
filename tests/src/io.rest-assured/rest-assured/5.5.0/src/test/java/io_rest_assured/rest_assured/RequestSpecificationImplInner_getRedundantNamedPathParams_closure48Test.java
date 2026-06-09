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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RequestSpecificationImplInner_getRedundantNamedPathParams_closure48Test {
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal.RequestSpecificationImpl"
            + "$_getRedundantNamedPathParams_closure48";

    @Test
    void rejectsNamedPathParametersWithoutMatchingPlaceholders() throws Throwable {
        String dynamicallyLoadedClassName = "io.restassured.internal.TestSpecificationImpl";
        Class<?> dynamicallyResolvedClass = invokeGeneratedClassLookup(dynamicallyLoadedClassName);
        assertThat(dynamicallyResolvedClass.getName()).isEqualTo(dynamicallyLoadedClassName);

        RestAssured.reset();
        try {
            assertThatThrownBy(() -> given()
                    .baseUri("http://127.0.0.1")
                    .port(1)
                    .pathParam("orderId", "order-123")
                    .pathParam("unused", "redundant-value")
                    .when()
                    .get("/orders/{orderId}"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid number of path parameters")
                    .hasMessageContaining("Expected 1, was 2")
                    .hasMessageContaining("Redundant path parameters are")
                    .hasMessageContaining("unused=redundant-value");
        } finally {
            RestAssured.reset();
        }
    }

    private static Class<?> invokeGeneratedClassLookup(String className) throws Throwable {
        ClassLoader classLoader = RequestSpecificationImplInner_getRedundantNamedPathParams_closure48Test.class
                .getClassLoader();
        Class<?> closureClass = classLoader.loadClass(CLOSURE_CLASS_NAME);
        Method classHelper = closureClass.getDeclaredMethod("class$", String.class);
        classHelper.setAccessible(true);
        return (Class<?>) classHelper.invoke(null, className);
    }
}
