/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_core;

import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.core.context.SecurityContextImpl;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class SecurityContextHolderTest {
    @Test
    void loadsConfiguredStrategyAndDelegatesContextLifecycle() {
        SecurityContextHolderStrategy original = SecurityContextHolder.getContextHolderStrategy();
        try {
            SecurityContextHolder.setStrategyName(CustomerContextStrategy.class.getName());
            assertInstanceOf(CustomerContextStrategy.class, SecurityContextHolder.getContextHolderStrategy());
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            assertNull(context.getAuthentication());
            Authentication authentication = new TestingAuthenticationToken("alex", "secret", "ROLE_READER");
            context.setAuthentication(authentication);

            SecurityContextHolder.setContext(context);

            assertSame(context, SecurityContextHolder.getContext());
            assertSame(authentication, SecurityContextHolder.getContext().getAuthentication());
            SecurityContextHolder.clearContext();
            assertNotSame(context, SecurityContextHolder.getContext());
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        } finally {
            SecurityContextHolder.clearContext();
            SecurityContextHolder.setContextHolderStrategy(original);
        }
    }

    public static class CustomerContextStrategy implements SecurityContextHolderStrategy {
        private final ThreadLocal<SecurityContext> contexts = ThreadLocal.withInitial(SecurityContextImpl::new);

        public CustomerContextStrategy() {
        }

        @Override
        public void clearContext() {
            contexts.remove();
        }

        @Override
        public SecurityContext getContext() {
            return contexts.get();
        }

        @Override
        public void setContext(SecurityContext context) {
            contexts.set(Objects.requireNonNull(context));
        }

        @Override
        public SecurityContext createEmptyContext() {
            return new SecurityContextImpl();
        }
    }
}
