/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import groovy.lang.Tuple2;
import io.restassured.builder.ResponseBuilder;
import io.restassured.filter.time.TimingFilter;
import io.restassured.internal.ResponseSpecificationImplTimeClosure2DirectAccess;
import io.restassured.internal.RestAssuredResponseImpl;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ResponseSpecificationImplInner_time_closure2Test {
    @Test
    void resolvesCompilerGeneratedClassResolver() throws Throwable {
        Class<?> resolvedClass = ResponseSpecificationImplTimeClosure2DirectAccess
                .resolveWithCompilerGeneratedClassResolver("groovy.lang.Tuple2");

        assertSame(Tuple2.class, resolvedClass);
    }

    @Test
    void validatesResponseTimeThroughResponseSpecification() {
        Response response = new ResponseBuilder()
                .setStatusCode(200)
                .setBody("{}")
                .build();
        RestAssuredResponseImpl restAssuredResponse = (RestAssuredResponseImpl) response;
        restAssuredResponse.setFilterContextProperties(Map.of(TimingFilter.RESPONSE_TIME_MILLISECONDS, 1L));

        response.then().time(lessThan(Long.MAX_VALUE), TimeUnit.MILLISECONDS);
    }
}
