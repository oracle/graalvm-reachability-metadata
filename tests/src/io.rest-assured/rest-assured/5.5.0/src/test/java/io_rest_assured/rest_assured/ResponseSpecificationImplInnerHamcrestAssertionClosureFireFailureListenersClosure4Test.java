/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.RestAssured;
import io.restassured.builder.ResponseBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.config.FailureConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.internal.ResponseSpecificationImplHamcrestAssertionClosureFireFailureListenersClosure4Access;
import io.restassured.response.Response;
import io.restassured.specification.ResponseSpecification;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResponseSpecificationImplInnerHamcrestAssertionClosureFireFailureListenersClosure4Test {
    private static final String ACTIVE_CLASS_NAME = "io.restassured.internal.ResponseSpecificationImpl"
            + Character.toString((char) 36)
            + "_HamcrestAssertionClosure_fireFailureListeners_closure4";

    @Test
    void resolvesClosureClassThroughCompilerGeneratedClassResolver() throws Throwable {
        Class<?> resolvedClass = ResponseSpecificationImplHamcrestAssertionClosureFireFailureListenersClosure4Access
                .resolveWithCompilerGeneratedClassResolver(ACTIVE_CLASS_NAME);

        assertEquals(ACTIVE_CLASS_NAME, resolvedClass.getName());
    }

    @Test
    void reportsMissingClassThroughCompilerGeneratedClassResolver() {
        NoClassDefFoundError error = assertThrows(
                NoClassDefFoundError.class,
                () -> ResponseSpecificationImplHamcrestAssertionClosureFireFailureListenersClosure4Access
                        .resolveWithCompilerGeneratedClassResolver(ACTIVE_CLASS_NAME + "Missing"));

        assertTrue(error.getMessage().endsWith("closure4Missing"));
    }

    @Test
    void notifiesConfiguredFailureListenerWhenResponseExpectationFails() {
        RestAssuredConfig previousConfig = RestAssured.config;
        AtomicInteger invocationCount = new AtomicInteger();
        AtomicReference<ResponseSpecification> observedResponseSpecification = new AtomicReference<>();
        AtomicReference<Response> observedResponse = new AtomicReference<>();
        try {
            RestAssured.config = RestAssuredConfig.config().failureConfig(FailureConfig.failureConfig()
                    .failureListeners((requestSpecification, responseSpecification, response) -> {
                        invocationCount.incrementAndGet();
                        observedResponseSpecification.set(responseSpecification);
                        observedResponse.set(response);
                    }));

            ResponseSpecification specification = new ResponseSpecBuilder()
                    .expectStatusCode(201)
                    .build();
            Response response = new ResponseBuilder()
                    .setStatusCode(500)
                    .setStatusLine("HTTP/1.1 500 Internal Server Error")
                    .setBody("failure")
                    .build();

            AssertionError error = assertThrows(AssertionError.class, () -> specification.validate(response));

            assertTrue(error.getMessage().contains("1 expectation failed"));
            assertEquals(1, invocationCount.get());
            assertSame(specification, observedResponseSpecification.get());
            assertSame(response, observedResponse.get());
        } finally {
            RestAssured.config = previousConfig;
        }
    }
}
