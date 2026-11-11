/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_core;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authorization.method.AuthorizationAdvisorProxyFactory;
import org.springframework.security.authorization.method.AuthorizationProxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AuthorizationAdvisorProxyFactoryInnerContainerTypeVisitorTest {
    @Test
    void securesEachArrayElementWithoutChangingTheOriginalArray() {
        DocumentView[] documents = {new Document("First"), new Document("Second")};
        AuthorizationAdvisorProxyFactory factory = AuthorizationAdvisorProxyFactory.withDefaults();

        DocumentView[] secured = factory.proxy(documents);

        assertNotSame(documents, secured);
        assertEquals(2, secured.length);
        assertEquals("First", secured[0].title());
        assertEquals("Second", secured[1].title());
        assertThrows(AccessDeniedException.class, secured[0]::secret);
        assertThrows(AccessDeniedException.class, secured[1]::secret);
        assertSame(documents[0], ((AuthorizationProxy) secured[0]).toAuthorizedTarget());
        assertEquals("confidential", documents[0].secret());
    }

    public interface DocumentView {
        @PreAuthorize("permitAll()")
        String title();

        @PreAuthorize("denyAll()")
        String secret();
    }

    public record Document(String title) implements DocumentView {
        @Override
        public String secret() {
            return "confidential";
        }
    }
}
