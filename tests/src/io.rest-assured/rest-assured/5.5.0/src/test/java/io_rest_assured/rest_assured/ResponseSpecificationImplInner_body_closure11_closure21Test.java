/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.builder.ResponseBuilder;
import io.restassured.internal.ResponseSpecificationImplBodyClosure11Closure21DirectAccess;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResponseSpecificationImplInner_body_closure11_closure21Test {
    @Test
    void resolvesNestedBodyClosureThroughCompilerGeneratedClassResolver() throws Throwable {
        String className = activeClosureClassName();

        Class<?> resolvedClass = ResponseSpecificationImplBodyClosure11Closure21DirectAccess
                .resolveWithCompilerGeneratedClassResolver(className);

        assertEquals(className, resolvedClass.getName());
    }

    @Test
    void resolvesRuntimeJdkClassThroughCompilerGeneratedClassResolver() throws Throwable {
        String className = String.join(".", "java", "util", "ArrayList");

        Class<?> resolvedClass = ResponseSpecificationImplBodyClosure11Closure21DirectAccess
                .resolveWithCompilerGeneratedClassResolver(className);

        assertEquals(className, resolvedClass.getName());
    }

    @Test
    void reportsMissingClassThroughCompilerGeneratedClassResolver() {
        NoClassDefFoundError error = assertThrows(
                NoClassDefFoundError.class,
                () -> ResponseSpecificationImplBodyClosure11Closure21DirectAccess
                        .resolveWithCompilerGeneratedClassResolver(activeClosureClassName() + "Missing"));

        assertTrue(error.getMessage().endsWith("closure21Missing"));
    }

    @Test
    void validatesAdditionalBodyKeyMatcherPairsThroughResponseSpecification() {
        Response response = new ResponseBuilder()
                .setContentType("application/json")
                .setBody("""
                        {
                          "book": {
                            "title": "Native Image Testing",
                            "pages": 64,
                            "authors": ["Ada", "Grace"]
                          }
                        }
                        """)
                .build();

        response.then()
                .body("book.title", equalTo("Native Image Testing"),
                        "book.pages", equalTo(64),
                        "book.authors[0]", equalTo("Ada"));
    }

    private static String activeClosureClassName() {
        return "io.restassured.internal.ResponseSpecificationImpl"
                + Character.toString((char) 36)
                + "_body_closure11_closure21";
    }
}
