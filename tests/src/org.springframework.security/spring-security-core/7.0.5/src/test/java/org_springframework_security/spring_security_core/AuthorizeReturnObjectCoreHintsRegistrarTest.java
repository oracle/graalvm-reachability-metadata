/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_core;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.security.aot.hint.AuthorizeReturnObjectCoreHintsRegistrar;
import org.springframework.security.authorization.method.AuthorizationAdvisorProxyFactory;
import org.springframework.security.authorization.method.AuthorizeReturnObject;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class AuthorizeReturnObjectCoreHintsRegistrarTest {
    @Test
    void registersAProxyForAnAnnotatedBeanReturnType() {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerBeanDefinition("catalog", new RootBeanDefinition(Catalog.class));
        RuntimeHints hints = new RuntimeHints();
        AuthorizeReturnObjectCoreHintsRegistrar registrar = new AuthorizeReturnObjectCoreHintsRegistrar(
                AuthorizationAdvisorProxyFactory.withDefaults());

        registrar.registerHints(hints, beans);

        assertTrue(hints.proxies().jdkProxyHints().anyMatch(hint -> hint.getProxiedInterfaces().contains(TypeReference.of(DocumentView.class))));
    }

    public static final class Catalog {
        @AuthorizeReturnObject
        public DocumentView document() {
            return () -> "public";
        }
    }

    public interface DocumentView {
        String title();
    }
}
