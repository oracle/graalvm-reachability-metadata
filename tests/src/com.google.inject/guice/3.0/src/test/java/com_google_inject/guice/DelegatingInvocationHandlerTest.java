/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_inject.guice;

import com.google.inject.Injector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DelegatingInvocationHandlerTest {
    @Test
    void forwardsCallsAndArgumentsAfterTheCircularDependencyIsConstructed() {
        Injector injector = CircularServices.newInjector();
        CircularServices.Service service = injector.getInstance(CircularServices.Service.class);
        CircularServices.Service circularReference = service.peer().service;
        assertEquals("Hello, Ada", circularReference.greet("Ada"));
        assertEquals("Hello, Grace", circularReference.greet("Grace"));
        assertEquals(service.greet("Lin"), circularReference.greet("Lin"));
    }
}
