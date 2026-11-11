/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_core;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.method.HandleAuthorizationDenied;
import org.springframework.security.authorization.method.MethodAuthorizationDeniedHandler;
import org.springframework.security.authorization.method.PreAuthorizeAuthorizationManager;
import org.springframework.security.util.MethodInvocationUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ReflectiveMethodAuthorizationDeniedHandlerTest {
    @Test
    void constructsTheAnnotatedHandlerWithoutAnApplicationContext() {
        MethodInvocation invocation = MethodInvocationUtils.create(new ProtectedDocument(), "read");
        assertNotNull(invocation);
        PreAuthorizeAuthorizationManager manager = new PreAuthorizeAuthorizationManager();
        AuthorizationResult decision = manager.authorize(() -> null, invocation);

        assertNotNull(decision);
        assertFalse(decision.isGranted());
        assertEquals("redacted:read", manager.handleDeniedInvocation(invocation, decision));
    }

    public static final class ProtectedDocument {
        @PreAuthorize("denyAll()")
        @HandleAuthorizationDenied(handlerClass = RedactingHandler.class)
        public String read() {
            return "confidential";
        }
    }

    public static final class RedactingHandler implements MethodAuthorizationDeniedHandler {
        public RedactingHandler() {
        }

        @Override
        public Object handleDeniedInvocation(MethodInvocation invocation, AuthorizationResult result) {
            return "redacted:" + invocation.getMethod().getName();
        }
    }
}
