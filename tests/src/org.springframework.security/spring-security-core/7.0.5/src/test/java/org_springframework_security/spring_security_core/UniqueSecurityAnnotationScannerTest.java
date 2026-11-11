/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_core;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.SecurityAnnotationScanner;
import org.springframework.security.core.annotation.SecurityAnnotationScanners;
import org.springframework.security.core.parameters.P;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class UniqueSecurityAnnotationScannerTest {
    @Test
    void findsAnInheritedMethodSecurityRule() throws NoSuchMethodException {
        Method method = AccountService.class.getMethod("read", String.class);
        SecurityAnnotationScanner<PreAuthorize> scanner = SecurityAnnotationScanners.requireUnique(PreAuthorize.class);
        PreAuthorize annotation = scanner.scan(method, AccountService.class);

        assertNotNull(annotation);
        assertEquals("hasAuthority('account:read')", annotation.value());
        assertSame(annotation, scanner.scan(method, AccountService.class));
    }

    @Test
    void findsAParameterAnnotationOnTheImplementedInterface() throws NoSuchMethodException {
        Method method = AccountService.class.getMethod("read", String.class);
        SecurityAnnotationScanner<P> scanner = SecurityAnnotationScanners.requireUnique(P.class);
        P annotation = scanner.scan(method.getParameters()[0]);

        assertNotNull(annotation);
        assertEquals("account", annotation.value());
    }

    public interface AccountOperations {
        @PreAuthorize("hasAuthority('account:read')")
        String read(@P("account") String account);
    }

    public static final class AccountService implements AccountOperations {
        @Override
        public String read(String account) {
            return "Account " + account;
        }
    }
}
