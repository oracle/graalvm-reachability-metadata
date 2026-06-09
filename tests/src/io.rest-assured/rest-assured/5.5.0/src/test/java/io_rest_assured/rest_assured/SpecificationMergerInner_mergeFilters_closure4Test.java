/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import io.restassured.specification.QueryableRequestSpecification;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SpecificationMergerInner_mergeFilters_closure4Test {
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal.SpecificationMerger"
            + "$_mergeFilters_closure4";

    @Test
    void mergingRequestSpecificationsAddsOnlyFiltersThatAreNotAlreadyPresent() {
        RestAssured.reset();
        try {
            Filter existingFilter = new HeaderFilter("X-Existing", "kept");
            Filter additionalFilter = new HeaderFilter("X-Additional", "merged");
            CapturingFilterList sourceFilters = new CapturingFilterList(
                    existingFilter, additionalFilter);
            RequestSpecification targetSpecification = new RequestSpecBuilder()
                    .addFilter(existingFilter)
                    .build();
            RequestSpecification sourceSpecification = RestAssured.given();
            ((GroovyObject) sourceSpecification).setProperty("filters", sourceFilters);

            targetSpecification.spec(sourceSpecification);

            Closure<?> filterSelection = sourceFilters.getFindAllPredicate();
            assertNotNull(filterSelection);
            assertEquals(CLOSURE_CLASS_NAME, filterSelection.getClass().getName());
            QueryableRequestSpecification queryableSpecification =
                    (QueryableRequestSpecification) targetSpecification;
            assertEquals(List.of(existingFilter, additionalFilter),
                    queryableSpecification.getDefinedFilters());
        } finally {
            RestAssured.reset();
        }
    }

    public static final class CapturingFilterList extends ArrayList<Filter> {
        private transient Closure<?> findAllPredicate;

        CapturingFilterList(Filter... filters) {
            super(Arrays.asList(filters));
        }

        public List<Filter> findAll(Closure<?> predicate) {
            findAllPredicate = predicate;
            return stream()
                    .filter(filter -> Boolean.TRUE.equals(predicate.call(filter)))
                    .toList();
        }

        Closure<?> getFindAllPredicate() {
            return findAllPredicate;
        }
    }

    private static final class HeaderFilter implements Filter {
        private final String headerName;
        private final String headerValue;

        private HeaderFilter(String headerName, String headerValue) {
            this.headerName = headerName;
            this.headerValue = headerValue;
        }

        @Override
        public Response filter(FilterableRequestSpecification requestSpecification,
                FilterableResponseSpecification responseSpecification, FilterContext context) {
            requestSpecification.header(headerName, headerValue);
            return context.next(requestSpecification, responseSpecification);
        }
    }
}
