/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_web;

import javax.servlet.http.HttpServletMapping;
import javax.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.springframework.security.web.FilterInvocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FilterInvocationInnerUnsupportedOperationExceptionInvocationHandlerTest {

    @Test
    void syntheticRequestProvidesDefaultServletMapping() {
        FilterInvocation invocation = new FilterInvocation("/app", "/orders", "GET");
        HttpServletRequest request = invocation.getHttpRequest();

        HttpServletMapping mapping = request.getHttpServletMapping();

        assertNotNull(mapping);
        assertEquals("", mapping.getMatchValue());
        assertEquals("", mapping.getPattern());
        assertEquals("", mapping.getServletName());
        assertNull(mapping.getMappingMatch());
        assertEquals("/app/orders", request.getRequestURI());
    }

    @Test
    void syntheticRequestHasNoServerPushBuilder() {
        FilterInvocation invocation = new FilterInvocation("/orders", "GET");

        assertNull(invocation.getHttpRequest().newPushBuilder());
    }

    @Test
    void syntheticRequestProvidesReadyEmptyTrailers() {
        FilterInvocation invocation = new FilterInvocation("/orders", "POST");
        HttpServletRequest request = invocation.getHttpRequest();

        assertTrue(request.isTrailerFieldsReady());
        assertTrue(request.getTrailerFields().isEmpty());
    }
}
