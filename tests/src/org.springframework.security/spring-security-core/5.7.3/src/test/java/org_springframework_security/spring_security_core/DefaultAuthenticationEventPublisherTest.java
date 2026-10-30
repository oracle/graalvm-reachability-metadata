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
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.ProviderNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureCredentialsExpiredEvent;
import org.springframework.security.authentication.event.AuthenticationFailureDisabledEvent;
import org.springframework.security.authentication.event.AuthenticationFailureExpiredEvent;
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent;
import org.springframework.security.authentication.event.AuthenticationFailureProviderNotFoundEvent;
import org.springframework.security.authentication.event.AuthenticationFailureServiceExceptionEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

public class DefaultAuthenticationEventPublisherTest {
    @Test
    void publishesMappedAuthenticationFailures() {
        List<Object> events = new ArrayList<>();
        DefaultAuthenticationEventPublisher publisher = new DefaultAuthenticationEventPublisher(events::add);
        Authentication authentication = new UsernamePasswordAuthenticationToken("alex", "secret");
        List<AuthenticationException> failures = List.of(
                new BadCredentialsException("credentials"), new UsernameNotFoundException("unknown user"),
                new AccountExpiredException("expired"), new ProviderNotFoundException("provider"),
                new DisabledException("disabled"), new LockedException("locked"),
                new AuthenticationServiceException("service"), new CredentialsExpiredException("credentials expired"));
        List<Class<? extends AbstractAuthenticationFailureEvent>> eventTypes = List.of(
                AuthenticationFailureBadCredentialsEvent.class, AuthenticationFailureBadCredentialsEvent.class,
                AuthenticationFailureExpiredEvent.class, AuthenticationFailureProviderNotFoundEvent.class,
                AuthenticationFailureDisabledEvent.class, AuthenticationFailureLockedEvent.class,
                AuthenticationFailureServiceExceptionEvent.class, AuthenticationFailureCredentialsExpiredEvent.class);

        for (int i = 0; i < failures.size(); i++) {
            publisher.publishAuthenticationFailure(failures.get(i), authentication);
            assertEquals(i + 1, events.size());
            AbstractAuthenticationFailureEvent event = assertInstanceOf(eventTypes.get(i), events.get(i));
            assertSame(authentication, event.getAuthentication());
            assertSame(failures.get(i), event.getException());
        }
    }

    @Test
    void loadsAdditionalEventMappingFromProperties() {
        List<Object> events = new ArrayList<>();
        DefaultAuthenticationEventPublisher publisher = new DefaultAuthenticationEventPublisher(events::add);
        Properties mappings = new Properties();
        mappings.setProperty(InsufficientAuthenticationException.class.getName(),
                AuthenticationFailureServiceExceptionEvent.class.getName());
        publisher.setAdditionalExceptionMappings(mappings);
        Authentication authentication = new UsernamePasswordAuthenticationToken("alex", "secret");
        AuthenticationException failure = new InsufficientAuthenticationException("more credentials required");

        publisher.publishAuthenticationFailure(failure, authentication);

        assertEquals(1, events.size());
        AuthenticationFailureServiceExceptionEvent event = assertInstanceOf(
                AuthenticationFailureServiceExceptionEvent.class, events.get(0));
        assertSame(authentication, event.getAuthentication());
        assertSame(failure, event.getException());
    }

    @Test
    void usesDefaultEventForUnmappedFailures() {
        List<Object> events = new ArrayList<>();
        DefaultAuthenticationEventPublisher publisher = new DefaultAuthenticationEventPublisher(events::add);
        publisher.setDefaultAuthenticationFailureEvent(AuthenticationFailureServiceExceptionEvent.class);
        Authentication authentication = new UsernamePasswordAuthenticationToken("alex", "secret");
        AuthenticationException failure = new InsufficientAuthenticationException("more credentials required");

        publisher.publishAuthenticationFailure(failure, authentication);

        assertEquals(1, events.size());
        AuthenticationFailureServiceExceptionEvent event = assertInstanceOf(
                AuthenticationFailureServiceExceptionEvent.class, events.get(0));
        assertSame(authentication, event.getAuthentication());
        assertSame(failure, event.getException());
    }
}
