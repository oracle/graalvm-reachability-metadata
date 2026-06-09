/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.util.List;
import java.util.function.Function;

import io.restassured.filter.Filter;
import io.restassured.filter.log.LogDetail;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.specification.RequestLogSpecification;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.SpecificationQuerier;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

public class RequestLogSpecificationImplTest {
    @Test
    void requestLogDslMethodsAddRequestLoggingFiltersToSpecification() {
        RequestSpecification requestSpecification = given()
                .header("X-Dynamic-Access", "enabled")
                .queryParam("mode", "headers")
                .body("request body");

        assertAddsRequestLoggingFilter(requestSpecification, RequestLogSpecification::params);
        assertAddsRequestLoggingFilter(requestSpecification, RequestLogSpecification::parameters);
        assertAddsRequestLoggingFilter(requestSpecification, RequestLogSpecification::uri);
        assertAddsRequestLoggingFilter(requestSpecification, RequestLogSpecification::method);
        assertAddsRequestLoggingFilter(requestSpecification, RequestLogSpecification::body);
        assertAddsRequestLoggingFilter(requestSpecification, log -> log.body(false));
        assertAddsRequestLoggingFilter(requestSpecification, RequestLogSpecification::all);
        assertAddsRequestLoggingFilter(requestSpecification, log -> log.all(false));
        assertAddsRequestLoggingFilter(requestSpecification, RequestLogSpecification::everything);
        assertAddsRequestLoggingFilter(requestSpecification, log -> log.everything(false));
        assertAddsRequestLoggingFilter(requestSpecification, RequestLogSpecification::headers);
        assertAddsRequestLoggingFilter(requestSpecification, RequestLogSpecification::cookies);
        assertAddsRequestLoggingFilter(requestSpecification, RequestLogSpecification::ifValidationFails);
        assertAddsRequestLoggingFilter(requestSpecification, log -> log.ifValidationFails(LogDetail.BODY));
        assertAddsRequestLoggingFilter(requestSpecification, log -> log.ifValidationFails(LogDetail.ALL, false));
    }

    private static void assertAddsRequestLoggingFilter(
            RequestSpecification requestSpecification,
            Function<RequestLogSpecification, RequestSpecification> loggingOperation
    ) {
        List<Filter> filtersBefore = SpecificationQuerier.query(requestSpecification).getDefinedFilters();
        int previousFilterCount = filtersBefore.size();

        RequestSpecification returnedSpecification = loggingOperation.apply(requestSpecification.log());

        List<Filter> filtersAfter = SpecificationQuerier.query(requestSpecification).getDefinedFilters();
        assertThat(returnedSpecification).isSameAs(requestSpecification);
        assertThat(filtersAfter).hasSize(previousFilterCount + 1);
        assertThat(filtersAfter.get(previousFilterCount)).isInstanceOf(RequestLoggingFilter.class);
    }
}
