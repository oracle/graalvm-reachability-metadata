/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.builder.ResponseBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.internal.ResponseSpecificationImplApplyArgumentsClosure15DirectAccess;
import io.restassured.response.Response;
import io.restassured.specification.Argument;
import io.restassured.specification.ResponseSpecification;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResponseSpecificationImplInner_applyArguments_closure15Test {
    @Test
    void resolvesApplyArgumentsClosureThroughCompilerGeneratedClassResolver() throws Throwable {
        String className = activeClosureClassName();

        Class<?> resolvedClass = ResponseSpecificationImplApplyArgumentsClosure15DirectAccess
                .resolveWithCompilerGeneratedClassResolver(className);

        assertEquals(className, resolvedClass.getName());
    }

    @Test
    void resolvesJdkClassKnownOnlyAtRuntimeThroughCompilerGeneratedClassResolver() throws Throwable {
        String className = String.join(".", "java", "lang", "String");

        Class<?> resolvedClass = ResponseSpecificationImplApplyArgumentsClosure15DirectAccess
                .resolveWithCompilerGeneratedClassResolver(className);

        assertEquals(String.class, resolvedClass);
    }

    @Test
    void reportsMissingClassThroughCompilerGeneratedClassResolver() {
        NoClassDefFoundError error = assertThrows(
                NoClassDefFoundError.class,
                () -> ResponseSpecificationImplApplyArgumentsClosure15DirectAccess
                        .resolveWithCompilerGeneratedClassResolver(activeClosureClassName() + "Missing"));

        assertTrue(error.getMessage().endsWith("closure15Missing"));
    }

    @Test
    void appliesFormattedBodyPathArgumentsThroughResponseSpecification() {
        ResponseSpecification specification = new ResponseSpecBuilder()
                .expectBody(
                        "store.%s[%d].title",
                        List.of(Argument.withArg("books"), Argument.withArg(0)),
                        equalTo("Effective Java"))
                .build();
        Response response = new ResponseBuilder()
                .setContentType("application/json")
                .setBody("""
                        {"store":{"books":[{"title":"Effective Java"}]}}
                        """)
                .build();

        response.then().spec(specification);
    }

    private static String activeClosureClassName() {
        return "io.restassured.internal.ResponseSpecificationImpl"
                + Character.toString((char) 36)
                + "_applyArguments_closure15";
    }
}
