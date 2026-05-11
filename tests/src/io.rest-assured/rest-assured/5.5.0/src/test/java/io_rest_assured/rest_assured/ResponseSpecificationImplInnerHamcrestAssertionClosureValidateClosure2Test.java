/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.builder.ResponseBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.internal.ResponseSpecificationImplHamcrestAssertionClosureValidateClosure2Access;
import io.restassured.internal.ResponseSpecificationImplHamcrestAssertionClosureValidateClosure2DirectAccess;
import io.restassured.response.Response;
import io.restassured.specification.ResponseSpecification;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResponseSpecificationImplInnerHamcrestAssertionClosureValidateClosure2Test {
    @Test
    void resolvesValidateClosureImplementationThroughCompilerGeneratedClassResolver() throws Throwable {
        String className = activeClosureClassName();

        Class<?> resolvedClass = ResponseSpecificationImplHamcrestAssertionClosureValidateClosure2DirectAccess
                .resolveWithCompilerGeneratedClassResolver(className);

        assertEquals(className, resolvedClass.getName());
    }

    @Test
    void resolvesRestAssuredClassKnownOnlyAtRuntimeThroughCompilerGeneratedClassResolver() throws Throwable {
        String className = String.join(".", "io", "restassured", "internal", "http", "CharsetExtractor");

        Class<?> resolvedClass = ResponseSpecificationImplHamcrestAssertionClosureValidateClosure2DirectAccess
                .resolveWithCompilerGeneratedClassResolver(className);

        assertEquals(className, resolvedClass.getName());
    }

    @Test
    void resolvesValidateClosureImplementationThroughJavaReflectionDispatch() throws Throwable {
        String className = activeClosureClassName();

        Class<?> resolvedClass = ResponseSpecificationImplHamcrestAssertionClosureValidateClosure2Access
                .resolveWithJavaReflectionDispatch(className);

        assertEquals(className, resolvedClass.getName());
    }

    @Test
    void reportsMissingClassThroughCompilerGeneratedClassResolver() {
        NoClassDefFoundError error = assertThrows(
                NoClassDefFoundError.class,
                () -> ResponseSpecificationImplHamcrestAssertionClosureValidateClosure2DirectAccess
                        .resolveWithCompilerGeneratedClassResolver(activeClosureClassName() + "Missing"));

        assertTrue(error.getMessage().endsWith("closure2Missing"));
    }

    @Test
    void filtersUnsuccessfulResponseValidationResultsThroughValidateClosure() {
        ResponseSpecification specification = new ResponseSpecBuilder()
                .expectStatusCode(200)
                .build();
        Response response = new ResponseBuilder()
                .setStatusCode(500)
                .setStatusLine("HTTP/1.1 500 Internal Server Error")
                .build();

        AssertionError error = assertThrows(AssertionError.class, () -> response.then().spec(specification));

        assertTrue(error.getMessage().contains("1 expectation failed."));
    }

    private static String activeClosureClassName() {
        return "io.restassured.internal.ResponseSpecificationImpl"
                + Character.toString((char) 36)
                + "_HamcrestAssertionClosure_validate_closure2";
    }

}
