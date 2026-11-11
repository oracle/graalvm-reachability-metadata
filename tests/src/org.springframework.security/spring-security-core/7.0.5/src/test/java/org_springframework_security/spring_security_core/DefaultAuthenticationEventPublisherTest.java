/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_core;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureServiceExceptionEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

public class DefaultAuthenticationEventPublisherTest {
    @Test
    void publishesDefaultAndConfiguredFailureEvents() {
        List<Object> events = new ArrayList<>();
        DefaultAuthenticationEventPublisher publisher = new DefaultAuthenticationEventPublisher(events::add);
        Authentication request = UsernamePasswordAuthenticationToken.unauthenticated("reader", "secret");
        BadCredentialsException badCredentials = new BadCredentialsException("Incorrect password");
        publisher.publishAuthenticationFailure(badCredentials, request);
        AbstractAuthenticationFailureEvent first = assertInstanceOf(AuthenticationFailureBadCredentialsEvent.class, events.get(0));
        assertSame(request, first.getAuthentication());
        assertSame(badCredentials, first.getException());

        Properties mappings = new Properties();
        mappings.setProperty(AuthenticationServiceException.class.getName(), AuthenticationFailureServiceExceptionEvent.class.getName());
        publisher.setAdditionalExceptionMappings(mappings);
        AuthenticationServiceException unavailable = new AuthenticationServiceException("Directory unavailable");
        publisher.publishAuthenticationFailure(unavailable, request);
        AbstractAuthenticationFailureEvent second = assertInstanceOf(AuthenticationFailureServiceExceptionEvent.class, events.get(1));
        assertSame(unavailable, second.getException());

        publisher.setDefaultAuthenticationFailureEvent(AuthenticationFailureServiceExceptionEvent.class);
        AuthenticationException unknown = new UnmappedAuthenticationException("Tenant unavailable");
        publisher.publishAuthenticationFailure(unknown, request);
        AbstractAuthenticationFailureEvent fallback = assertInstanceOf(AuthenticationFailureServiceExceptionEvent.class, events.get(2));
        assertSame(request, fallback.getAuthentication());
        assertSame(unknown, fallback.getException());
        assertEquals(3, events.size());
    }

    public static final class UnmappedAuthenticationException extends AuthenticationException {
        public UnmappedAuthenticationException(String message) {
            super(message);
        }
    }
}
