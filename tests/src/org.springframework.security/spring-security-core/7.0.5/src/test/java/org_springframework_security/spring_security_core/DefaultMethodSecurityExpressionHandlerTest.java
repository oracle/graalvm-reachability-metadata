/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_core;

import java.util.List;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.util.MethodInvocationUtils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class DefaultMethodSecurityExpressionHandlerTest {
    @Test
    void filtersAnArrayWhilePreservingItsComponentType() {
        MethodInvocation invocation = MethodInvocationUtils.create(new DocumentService(), "documents");
        assertNotNull(invocation);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated("reader", "secret",
                List.of(new SimpleGrantedAuthority("ROLE_READER")));
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        EvaluationContext context = handler.createEvaluationContext(() -> authentication, invocation);
        Expression expression = handler.getExpressionParser().parseExpression("filterObject != 'private' and hasRole('READER')");
        String[] documents = {"public", "private", "shared"};

        String[] filtered = assertInstanceOf(String[].class, handler.filter(documents, expression, context));

        assertArrayEquals(new String[]{"public", "shared"}, filtered);
        assertNotSame(documents, filtered);
        assertArrayEquals(new String[]{"public", "private", "shared"}, documents);
    }

    public static final class DocumentService {
        public String[] documents() {
            return new String[]{"public", "private", "shared"};
        }
    }
}
