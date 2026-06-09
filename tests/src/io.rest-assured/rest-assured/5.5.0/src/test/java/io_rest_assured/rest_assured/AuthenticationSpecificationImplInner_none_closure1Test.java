/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;

import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import io.restassured.RestAssured;
import io.restassured.authentication.ExplicitNoAuthScheme;
import io.restassured.filter.Filter;
import io.restassured.spi.AuthFilter;
import io.restassured.specification.QueryableRequestSpecification;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.specification.SpecificationQuerier.query;
import static org.assertj.core.api.Assertions.assertThat;

public class AuthenticationSpecificationImplInner_none_closure1Test {
    private static final String AUTHORIZATION_HEADER_NAME = "Authorization";

    @BeforeEach
    void resetRestAssuredDefaultsBeforeTest() {
        RestAssured.reset();
    }

    @AfterEach
    void resetRestAssuredDefaultsAfterTest() {
        RestAssured.reset();
    }

    @Test
    void noneRemovesAuthenticationFiltersAndAuthorizationHeader() throws Throwable {
        AuthFilter authenticationFilter = (requestSpec, responseSpec, context) -> context.next(requestSpec, responseSpec);
        Filter applicationFilter = (requestSpec, responseSpec, context) -> context.next(requestSpec, responseSpec);
        CapturingFilterList filters = new CapturingFilterList(applicationFilter, authenticationFilter);
        RequestSpecification requestSpecification = given()
                .header(AUTHORIZATION_HEADER_NAME, "Bearer token");
        ((GroovyObject) requestSpecification).setProperty("filters", filters);

        requestSpecification.auth().none();

        QueryableRequestSpecification queryableRequestSpecification = query(requestSpecification);
        assertThat(queryableRequestSpecification.getAuthenticationScheme()).isInstanceOf(ExplicitNoAuthScheme.class);
        assertThat(queryableRequestSpecification.getDefinedFilters()).containsExactly(applicationFilter);
        assertThat(queryableRequestSpecification.getHeaders().hasHeaderWithName(AUTHORIZATION_HEADER_NAME)).isFalse();
        assertThat(filters.getRemovalPredicate()).isNotNull();

        Class<?> resolvedClass = invokeGeneratedClassLookup(filters.getRemovalPredicate(), AuthFilter.class.getName());
        assertThat(resolvedClass).isEqualTo(AuthFilter.class);
    }

    private static Class<?> invokeGeneratedClassLookup(Closure<?> closure, String className) throws Throwable {
        Class<?> closureClass = closure.getClass();
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(closureClass, MethodHandles.lookup());
        MethodHandle classHelper = lookup.findStatic(
                closureClass,
                "class$",
                MethodType.methodType(Class.class, String.class));
        return (Class<?>) classHelper.invoke(className);
    }

    public static final class CapturingFilterList extends ArrayList<Filter> {
        private transient Closure<?> removalPredicate;

        CapturingFilterList(Filter... filters) {
            super(Arrays.asList(filters));
        }

        public boolean removeAll(Closure<?> predicate) {
            removalPredicate = predicate;
            return removeIf(filter -> Boolean.TRUE.equals(predicate.call(filter)));
        }

        Closure<?> getRemovalPredicate() {
            return removalPredicate;
        }
    }
}
