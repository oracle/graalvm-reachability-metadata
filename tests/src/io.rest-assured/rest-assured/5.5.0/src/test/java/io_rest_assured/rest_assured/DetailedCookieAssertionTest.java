/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.assertion.DetailedCookieAssertion;
import io.restassured.builder.ResponseBuilder;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static io.restassured.matcher.RestAssuredMatchers.detailedCookie;
import static org.junit.jupiter.api.Assertions.assertSame;

public class DetailedCookieAssertionTest {
    @Test
    void resolvesCompilerGeneratedClassResolver() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                DetailedCookieAssertion.class,
                MethodHandles.lookup());
        MethodHandle classResolver = lookup.findStatic(
                DetailedCookieAssertion.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        Class<?> resolvedClass = (Class<?>) classResolver.invokeExact(
                "io.restassured.assertion.DetailedCookieAssertion");

        assertSame(DetailedCookieAssertion.class, resolvedClass);
    }

    @Test
    void validatesDetailedCookieFromSetCookieHeader() {
        Response response = new ResponseBuilder()
                .setStatusCode(200)
                .setBody("ok")
                .setHeader("Set-Cookie", "session=abc123; Path=/; HttpOnly; Secure")
                .build();

        response.then()
                .assertThat()
                .cookie("session", detailedCookie()
                        .value("abc123")
                        .path("/")
                        .httpOnly(true)
                        .secured(true));
    }
}
