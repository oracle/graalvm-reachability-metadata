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
import org.springframework.security.aot.hint.AuthorizeReturnObjectHintsRegistrar;
import org.springframework.security.authorization.method.AuthorizationAdvisorProxyFactory;
import org.springframework.security.authorization.method.AuthorizeReturnObject;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class AuthorizeReturnObjectHintsRegistrarTest {
    @Test
    void traversesNestedAuthorizedReturnTypes() {
        RuntimeHints hints = new RuntimeHints();
        AuthorizeReturnObjectHintsRegistrar registrar = new AuthorizeReturnObjectHintsRegistrar(
                AuthorizationAdvisorProxyFactory.withDefaults(), FolderView.class);

        registrar.registerHints(hints, new DefaultListableBeanFactory());

        assertTrue(hints.proxies().jdkProxyHints().anyMatch(hint -> hint.getProxiedInterfaces().contains(TypeReference.of(FolderView.class))));
        assertTrue(hints.proxies().jdkProxyHints().anyMatch(hint -> hint.getProxiedInterfaces().contains(TypeReference.of(DocumentView.class))));
    }

    public interface FolderView {
        @AuthorizeReturnObject
        DocumentView document();
    }

    public interface DocumentView {
        String title();
    }
}
