/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.assertion.HeaderMatcher;
import io.restassured.builder.ResponseBuilder;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertSame;

public class HeaderMatcherTest {
    @Test
    void resolvesCompilerGeneratedClassResolverThroughGroovyDispatch() {
        HeaderMatcher matcher = new HeaderMatcher();

        Object resolvedClass = matcher.invokeMethod(
                "class$",
                new Object[] {"io.restassured.assertion.HeaderMatcher"});

        assertSame(HeaderMatcher.class, resolvedClass);
    }

    @Test
    void validatesResponseHeaderUsingMappingFunction() {
        Response response = new ResponseBuilder()
                .setStatusCode(200)
                .setBody("ok")
                .setHeader("X-Items", "3")
                .build();

        response.then()
                .assertThat()
                .header("X-Items", Integer::parseInt, equalTo(3));
    }
}
