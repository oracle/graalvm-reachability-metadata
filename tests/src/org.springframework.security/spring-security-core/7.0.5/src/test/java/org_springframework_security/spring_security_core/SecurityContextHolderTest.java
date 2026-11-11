/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_core;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.core.context.SecurityContextImpl;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class SecurityContextHolderTest {
    @Test
    void loadsAndUsesAnApplicationStrategyByName() {
        SecurityContextHolderStrategy original = SecurityContextHolder.getContextHolderStrategy();
        try {
            SecurityContextHolder.setStrategyName(SessionContextStrategy.class.getName());
            assertInstanceOf(SessionContextStrategy.class, SecurityContextHolder.getContextHolderStrategy());
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.unauthenticated("reader", "secret");
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

            assertSame(context, SecurityContextHolder.getContext());
            assertSame(authentication, SecurityContextHolder.getDeferredContext().get().getAuthentication());
            SecurityContextHolder.clearContext();
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        } finally {
            SecurityContextHolder.clearContext();
            SecurityContextHolder.setContextHolderStrategy(original);
        }
    }

    public static final class SessionContextStrategy implements SecurityContextHolderStrategy {
        private final ThreadLocal<SecurityContext> contexts = ThreadLocal.withInitial(SecurityContextImpl::new);

        public SessionContextStrategy() {
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
            contexts.set(context);
        }

        @Override
        public SecurityContext createEmptyContext() {
            return new SecurityContextImpl();
        }
    }
}
