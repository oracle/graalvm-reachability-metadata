/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_core;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeHint;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.aot.hint.PrePostAuthorizeExpressionBeanHintsRegistrar;
import org.springframework.security.authorization.method.AuthorizeReturnObject;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PrePostAuthorizeExpressionBeanHintsRegistrarTest {
    @Test
    void registersOnlyBeansReferencedBySecurityExpressionsIncludingNestedReturns() {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("readPolicy", new ReadPolicy());
        beans.registerSingleton("resultPolicy", new ResultPolicy());
        beans.registerSingleton("nestedPolicy", new NestedPolicy());
        beans.registerSingleton("unused", new Object());
        RuntimeHints hints = new RuntimeHints();
        PrePostAuthorizeExpressionBeanHintsRegistrar registrar = new PrePostAuthorizeExpressionBeanHintsRegistrar(SecuredCatalog.class);

        registrar.registerHints(hints, beans);

        for (Class<?> policy : new Class<?>[]{ReadPolicy.class, ResultPolicy.class, NestedPolicy.class}) {
            TypeHint hint = hints.reflection().getTypeHint(policy);
            assertNotNull(hint);
            assertTrue(hint.getMemberCategories().contains(MemberCategory.INVOKE_DECLARED_METHODS));
        }
        assertNull(hints.reflection().getTypeHint(Object.class));
    }

    public interface SecuredCatalog {
        @PreAuthorize("@readPolicy.allowed()")
        @PostAuthorize("@resultPolicy.allowed()")
        @AuthorizeReturnObject
        SecuredDocument document();
    }

    public interface SecuredDocument {
        @PreAuthorize("@nestedPolicy.allowed()")
        String title();
    }

    public static final class ReadPolicy {
        public boolean allowed() {
            return true;
        }
    }

    public static final class ResultPolicy {
        public boolean allowed() {
            return true;
        }
    }

    public static final class NestedPolicy {
        public boolean allowed() {
            return true;
        }
    }
}
