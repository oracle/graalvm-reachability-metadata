/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_core;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.util.MethodInvocationUtils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class DefaultMethodSecurityExpressionHandlerTest {
    @Test
    void filtersTypedArrayUsingAuthenticatedUser() {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        DocumentService service = new DocumentService();
        MethodInvocation invocation = MethodInvocationUtils.create(service, "owners");
        TestingAuthenticationToken authentication = new TestingAuthenticationToken("alex", "secret", "ROLE_READER");
        EvaluationContext context = handler.createEvaluationContext(authentication, invocation);
        Expression expression = handler.getExpressionParser().parseExpression(
                "filterObject == authentication.name or filterObject == 'shared'");
        String[] owners = service.owners();

        Object result = handler.filter(owners, expression, context);

        String[] filtered = assertInstanceOf(String[].class, result);
        assertArrayEquals(new String[]{"alex", "shared"}, filtered);
        assertNotSame(owners, filtered);
        assertArrayEquals(new String[]{"alex", "other", "shared"}, owners);

        Expression deny = handler.getExpressionParser().parseExpression("false");
        assertArrayEquals(new String[0], assertInstanceOf(String[].class, handler.filter(owners, deny, context)));
    }

    public static class DocumentService {
        public String[] owners() {
            return new String[]{"alex", "other", "shared"};
        }
    }
}
