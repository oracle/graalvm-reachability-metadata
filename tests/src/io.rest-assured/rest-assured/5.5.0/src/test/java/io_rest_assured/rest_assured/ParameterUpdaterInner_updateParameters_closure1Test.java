/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.restassured.RestAssured;
import io.restassured.specification.QueryableRequestSpecification;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ParameterUpdaterInner_updateParameters_closure1Test {
    @Test
    void mapQueryParametersUpdateScalarAndCollectionValues() {
        RestAssured.reset();
        try {
            Map<String, Object> parameters = new LinkedHashMap<>();
            parameters.put("category", "books");
            parameters.put("tag", List.of("java", "native"));

            RequestSpecification specification = RestAssured.given().queryParams(parameters);

            Map<String, ?> queryParameters = ((QueryableRequestSpecification) specification).getQueryParams();
            assertThat(queryParameters.get("category")).isEqualTo("books");
            assertThat(queryParameters.get("tag")).isEqualTo(List.of("java", "native"));
        } finally {
            RestAssured.reset();
        }
    }
}
