/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RequestSpecificationImplInner_getUndefinedPathParamPlaceholders_closure46Test {
    @Test
    void rejectsUndefinedPathParameterPlaceholdersBeforeSendingRequest() throws Throwable {
        RestAssured.reset();

        try {
            assertThatThrownBy(() -> given()
                    .baseUri("http://127.0.0.1")
                    .port(1)
                    .when()
                    .get("/orders/{orderId}/lines/{lineId}", "order-123"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid number of path parameters")
                    .hasMessageContaining("Expected 2, was 1")
                    .hasMessageContaining("Undefined path parameters are: lineId");
        } finally {
            RestAssured.reset();
        }
    }

}
