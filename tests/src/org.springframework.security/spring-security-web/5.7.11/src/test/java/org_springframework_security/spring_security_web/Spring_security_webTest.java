/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_web;

import org.junit.jupiter.api.Test;
import org.springframework.security.web.FilterInvocation;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Spring_security_webTest {
    @Test
    void syntheticInvocationRetainsRequestPathAndQuery() {
        FilterInvocation invocation = new FilterInvocation(
                "/store", "/orders", "/42", "view=summary", "GET");

        assertEquals("/orders/42?view=summary", invocation.getRequestUrl());
        assertEquals("/store/orders/42", invocation.getHttpRequest().getRequestURI());
        assertEquals("GET", invocation.getHttpRequest().getMethod());
        assertEquals("filter invocation [GET /orders/42?view=summary]", invocation.toString());
    }
}
