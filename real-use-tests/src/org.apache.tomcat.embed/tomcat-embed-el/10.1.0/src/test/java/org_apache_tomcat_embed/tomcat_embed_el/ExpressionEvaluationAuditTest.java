/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_el;

import jakarta.el.ELContext;
import jakarta.el.ELProcessor;
import jakarta.el.EvaluationListener;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audits Jakarta Expression Language evaluation events for a request policy decision.
 * An application can record the expressions and bean properties used while it evaluates
 * a policy, without changing the policy result returned to the request handler.
 */
public class ExpressionEvaluationAuditTest {

    @Test
    @Timeout(60)
    public void recordsPolicyEvaluationAndResolvedProperties() {
        EvaluationAudit audit = new EvaluationAudit();
        ELProcessor expressions = new ELProcessor();
        expressions.getELManager().addEvaluationListener(audit);
        expressions.defineBean("order", new Order("ORD-2048", "READY", 149));

        String decision = expressions.eval(
                "order.status == 'READY' && order.total >= 100 ? 'dispatch' : 'hold'");
        String reference = expressions.eval("order.number");

        assertEquals("dispatch", decision);
        assertEquals("ORD-2048", reference);
        assertEquals(List.of(
                "${order.status == 'READY' && order.total >= 100 ? 'dispatch' : 'hold'}",
                "${order.number}"), audit.beforeExpressions);
        assertEquals(audit.beforeExpressions, audit.afterExpressions);
        assertTrue(audit.resolvedProperties.containsAll(List.of("status", "total", "number")));
    }

    /** Receives the expression lifecycle notifications used by the application's audit sink. */
    public static final class EvaluationAudit extends EvaluationListener {
        private final List<String> beforeExpressions = new ArrayList<>();
        private final List<String> afterExpressions = new ArrayList<>();
        private final List<String> resolvedProperties = new ArrayList<>();

        @Override
        public void beforeEvaluation(ELContext context, String expression) {
            beforeExpressions.add(expression);
        }

        @Override
        public void afterEvaluation(ELContext context, String expression) {
            afterExpressions.add(expression);
        }

        @Override
        public void propertyResolved(ELContext context, Object base, Object property) {
            resolvedProperties.add(String.valueOf(property));
        }
    }

    /** Order data exposed to the policy expression by the request application. */
    public static final class Order {
        private final String number;
        private final String status;
        private final int total;

        public Order(String number, String status, int total) {
            this.number = number;
            this.status = status;
            this.total = total;
        }

        public String getNumber() {
            return number;
        }

        public String getStatus() {
            return status;
        }

        public int getTotal() {
            return total;
        }
    }
}
