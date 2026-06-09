/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.Cookies;
import io.restassured.specification.QueryableRequestSpecification;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SpecificationMergerInner_mergeSessionId_closure2Test {
    @Test
    void mergingRequestSpecificationsReplacesOldSessionCookieAndKeepsOtherCookies() {
        RequestSpecification overridingSpecification = new RequestSpecBuilder()
                .setSessionId("JSESSIONID", "new-session")
                .addCookie("locale", "en-US")
                .build();
        RequestSpecification mergedSpecification = new RequestSpecBuilder()
                .addCookie("theme", "dark")
                .setSessionId("JSESSIONID", "old-session")
                .addRequestSpecification(overridingSpecification)
                .build();

        Cookies cookies = ((QueryableRequestSpecification) mergedSpecification).getCookies();

        assertThat(cookies.getValue("JSESSIONID")).isEqualTo("new-session");
        assertThat(cookies.getValue("theme")).isEqualTo("dark");
        assertThat(cookies.getValue("locale")).isEqualTo("en-US");
        assertThat(cookies.getValues("JSESSIONID")).containsExactly("new-session");
    }
}
