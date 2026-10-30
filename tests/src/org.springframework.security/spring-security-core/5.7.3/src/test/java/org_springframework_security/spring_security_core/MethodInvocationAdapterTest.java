/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_core;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.aspectj.lang.JoinPoint;
import org.aspectj.runtime.reflect.Factory;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.annotation.SecuredAnnotationSecurityMetadataSource;
import org.springframework.security.access.intercept.aspectj.AspectJCallback;
import org.springframework.security.access.intercept.aspectj.AspectJMethodSecurityInterceptor;
import org.springframework.security.access.vote.AffirmativeBased;
import org.springframework.security.access.vote.RoleVoter;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MethodInvocationAdapterTest {
    @Test
    void authorizesPublicMethodThroughAspectJInterceptor() {
        SecuredDocuments documents = new SecuredDocuments();
        assertSecuredInvocation(documents, "read", Modifier.PUBLIC, () -> documents.read("budget"));
    }

    @Test
    void authorizesPrivateMethodThroughAspectJInterceptor() {
        SecuredDocuments documents = new SecuredDocuments();
        assertSecuredInvocation(documents, "readInternal", Modifier.PRIVATE, () -> documents.readInternal("budget"));
    }

    private void assertSecuredInvocation(SecuredDocuments documents, String methodName, int modifiers,
            AspectJCallback callback) {
        AspectJMethodSecurityInterceptor interceptor = new AspectJMethodSecurityInterceptor();
        interceptor.setSecurityMetadataSource(new SecuredAnnotationSecurityMetadataSource());
        interceptor.setAccessDecisionManager(new AffirmativeBased(List.of(new RoleVoter())));
        interceptor.afterPropertiesSet();
        Factory factory = new Factory("SecuredDocuments.java", SecuredDocuments.class);
        JoinPoint.StaticPart staticPart = factory.makeMethodSJP(JoinPoint.METHOD_EXECUTION,
                modifiers, methodName, SecuredDocuments.class, new Class<?>[]{String.class},
                new String[]{"name"}, new Class<?>[0], String.class, 1);
        JoinPoint joinPoint = Factory.makeJP(staticPart, documents, documents, new Object[]{"budget"});
        SecurityContext original = SecurityContextHolder.getContext();
        try {
            SecurityContextHolder.setContext(new SecurityContextImpl(
                    new TestingAuthenticationToken("alex", "secret", "ROLE_READER")));

            assertEquals("document:budget", interceptor.invoke(joinPoint, callback));

            SecurityContextHolder.setContext(new SecurityContextImpl(
                    new TestingAuthenticationToken("visitor", "secret", "ROLE_VISITOR")));
            AtomicBoolean proceeded = new AtomicBoolean();
            assertThrows(AccessDeniedException.class, () -> interceptor.invoke(joinPoint, () -> {
                proceeded.set(true);
                return callback.proceedWithObject();
            }));
            assertFalse(proceeded.get());
        } finally {
            SecurityContextHolder.setContext(original);
        }
    }

    public static class SecuredDocuments {
        @Secured("ROLE_READER")
        public String read(String name) {
            return "document:" + name;
        }

        @Secured("ROLE_READER")
        private String readInternal(String name) {
            return "document:" + name;
        }
    }
}
