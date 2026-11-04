/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ognl_ognl;

import ognl.Evaluation;
import ognl.Ognl;
import ognl.OgnlContext;
import ognl.OgnlException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises OGNL's evaluation-tracing subsystem for a support dashboard.
 *
 * A support application records the evaluated expression tree alongside a rendered dashboard
 * value so an operator can understand which profile properties contributed to the result.
 */
public class SupportDashboardEvaluationTraceTest {
    @Test
    @Timeout(60)
    void recordsDashboardExpressionTreeAndResult() throws OgnlException {
        SupportDashboard dashboard = SupportDashboard.sample();
        OgnlContext context = (OgnlContext) Ognl.createDefaultContext(dashboard);
        context.setTraceEvaluations(true);
        context.setKeepLastEvaluation(true);

        Object dashboardLabel = Ognl.getValue(
                "agentName + ' / open tickets: ' + openTickets.size()",
                context,
                dashboard);
        Evaluation evaluation = context.getLastEvaluation();
        String trace = evaluation.toString();

        assertThat(dashboardLabel).isEqualTo("Mina Patel / open tickets: 2");
        assertThat(evaluation.getSource()).isSameAs(dashboard);
        assertThat(evaluation.getResult()).isEqualTo("Mina Patel / open tickets: 2");
        assertThat(trace)
                .contains("ASTProperty")
                .contains("ASTChain")
                .contains("ASTMethod")
                .contains("Mina Patel / open tickets: 2");
    }

    public static final class SupportDashboard {
        private final String agentName;
        private final List<Ticket> openTickets;

        private SupportDashboard(String agentName, List<Ticket> openTickets) {
            this.agentName = agentName;
            this.openTickets = openTickets;
        }

        static SupportDashboard sample() {
            return new SupportDashboard(
                    "Mina Patel",
                    List.of(new Ticket("SR-2048"), new Ticket("SR-2049")));
        }

        public String getAgentName() {
            return agentName;
        }

        public List<Ticket> getOpenTickets() {
            return openTickets;
        }
    }

    public static final class Ticket {
        private final String reference;

        private Ticket(String reference) {
            this.reference = reference;
        }

        public String getReference() {
            return reference;
        }
    }
}
