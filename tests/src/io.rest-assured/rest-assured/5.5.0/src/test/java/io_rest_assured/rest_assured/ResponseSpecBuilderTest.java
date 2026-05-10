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
import io.restassured.parsing.Parser;
import io.restassured.response.Response;
import io.restassured.specification.ResponseSpecification;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;

public class ResponseSpecBuilderTest {
    @Test
    void copiesGlobalParserRegistrationsIntoBuiltSpecification() {
        String vendorJson = "application/vnd.response-spec-builder+json";
        RestAssured.registerParser(vendorJson, Parser.JSON);
        try {
            ResponseSpecification specification = new ResponseSpecBuilder()
                    .expectStatusCode(202)
                    .expectContentType(vendorJson)
                    .expectHeader("X-Source", "builder")
                    .expectBody("message", equalTo("parsed"))
                    .build();

            Response response = new ResponseBuilder()
                    .setStatusCode(202)
                    .setContentType(vendorJson)
                    .setHeader("X-Source", "builder")
                    .setBody("{\"message\":\"parsed\"}")
                    .build();

            response.then().spec(specification);
        } finally {
            RestAssured.reset();
        }
    }
}
