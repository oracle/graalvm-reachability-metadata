/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.builder.ResponseBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.internal.ResponseSpecificationImplHamcrestAssertionClosureValidateClosure3Access;
import io.restassured.response.Response;
import io.restassured.specification.ResponseSpecification;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResponseSpecificationImplInnerHamcrestAssertionClosureValidateClosure3Test {
    @Test
    void resolvesValidateClosureImplementationThroughCompilerGeneratedClassResolver() throws Throwable {
        String className = activeClosureClassName();

        Class<?> resolvedClass = resolveWithCompilerGeneratedClassResolver(className);

        assertEquals(className, resolvedClass.getName());
    }

    @Test
    void resolvesRestAssuredClassKnownOnlyAtRuntimeThroughCompilerGeneratedClassResolver() throws Throwable {
        String className = String.join(".", "io", "restassured", "internal", "http", "CharsetExtractor");

        Class<?> resolvedClass = resolveWithCompilerGeneratedClassResolver(className);

        assertEquals(className, resolvedClass.getName());
    }

    @Test
    void resolvesRestAssuredClassKnownOnlyAtRuntimeThroughJavaReflectionDispatch() throws Throwable {
        String className = String.join(".", "io", "restassured", "internal", "http", "CharsetExtractor");

        Class<?> resolvedClass = ResponseSpecificationImplHamcrestAssertionClosureValidateClosure3Access
                .resolveWithJavaReflectionDispatch(className);

        assertEquals(className, resolvedClass.getName());
    }

    @Test
    void reportsMissingClassThroughCompilerGeneratedClassResolver() {
        NoClassDefFoundError error = assertThrows(
                NoClassDefFoundError.class,
                () -> resolveWithCompilerGeneratedClassResolver(activeClosureClassName() + "Missing"));

        assertTrue(error.getMessage().endsWith("closure3Missing"));
    }

    @Test
    void filtersMultipleUnsuccessfulValidationResultsThroughValidateClosure() {
        ResponseSpecification specification = new ResponseSpecBuilder()
                .expectStatusCode(200)
                .expectStatusLine("HTTP/1.1 200 OK")
                .build();
        Response response = new ResponseBuilder()
                .setStatusCode(500)
                .setStatusLine("HTTP/1.1 500 Internal Server Error")
                .build();

        AssertionError error = assertThrows(AssertionError.class, () -> response.then().spec(specification));

        assertTrue(error.getMessage().contains("2 expectations failed."));
        assertTrue(error.getMessage().contains("Expected status line"));
    }

    private static Class<?> resolveWithCompilerGeneratedClassResolver(String className) throws Throwable {
        return ResponseSpecificationImplHamcrestAssertionClosureValidateClosure3Access
                .resolveWithGeneratedDirectAccess(className);
    }

    private static String activeClosureClassName() {
        return "io.restassured.internal.ResponseSpecificationImpl"
                + Character.toString((char) 36)
                + "_HamcrestAssertionClosure_validate_closure3";
    }
}
