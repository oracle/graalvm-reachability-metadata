/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_batch.spring_batch_core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.configuration.xml.SimpleFlowFactoryBean;
import org.springframework.batch.core.job.flow.FlowExecutionStatus;
import org.springframework.batch.core.job.flow.FlowExecutor;
import org.springframework.batch.core.job.flow.support.SimpleFlow;
import org.springframework.batch.core.job.flow.support.StateTransition;
import org.springframework.batch.core.job.flow.support.state.AbstractState;

public class SimpleFlowFactoryBeanTest {
    @Test
    void createsAndInitializesNamedFlow() throws Exception {
        SimpleFlowFactoryBean factory = new SimpleFlowFactoryBean();
        factory.setName("importFlow");
        factory.setFlowType(SimpleFlow.class);
        factory.setStateTransitions(List.of(StateTransition.createEndStateTransition(new CompletionState("load"))));
        factory.afterPropertiesSet();

        SimpleFlow flow = factory.getObject();

        assertEquals("importFlow", flow.getName());
        assertNotNull(flow.getState("importFlow.load"));
        assertEquals("importFlow.load", flow.getStartState().getName());
    }

    private static final class CompletionState extends AbstractState {
        private CompletionState(String name) {
            super(name);
        }

        @Override
        public FlowExecutionStatus handle(FlowExecutor executor) {
            return FlowExecutionStatus.COMPLETED;
        }

        @Override
        public boolean isEndState() {
            return true;
        }
    }
}
