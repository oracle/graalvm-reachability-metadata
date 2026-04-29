/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;

import org.apache.seata.rm.tcc.api.BusinessActionContext;
import org.apache.seata.rm.tcc.api.TwoPhaseBusinessAction;
import org.apache.seata.spring.tcc.TccAnnotationProcessor;
import org.junit.jupiter.api.Test;

public class TccAnnotationProcessorTest {
    @Test
    void inspectsAnnotatedTccReferenceBeforeReplacingField() throws Exception {
        TccAnnotationProcessor processor = new TccAnnotationProcessor();
        ServiceHolder holder = new ServiceHolder();
        Field serviceField = ServiceHolder.class.getField("service");

        assertThatThrownBy(() -> processor.addTccAdvise(
                holder,
                "serviceHolder",
                serviceField,
                TccParticipant.class))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(holder.service).isInstanceOf(SampleTccParticipant.class);
    }

    public interface TccParticipant {
        @TwoPhaseBusinessAction(
                name = "processorAction",
                commitMethod = "commitProcessorAction",
                rollbackMethod = "rollbackProcessorAction")
        boolean prepareProcessorAction(BusinessActionContext context);

        boolean commitProcessorAction(BusinessActionContext context);

        boolean rollbackProcessorAction(BusinessActionContext context);
    }

    public static class SampleTccParticipant implements TccParticipant {
        @Override
        public boolean prepareProcessorAction(BusinessActionContext context) {
            return true;
        }

        @Override
        public boolean commitProcessorAction(BusinessActionContext context) {
            return true;
        }

        @Override
        public boolean rollbackProcessorAction(BusinessActionContext context) {
            return true;
        }
    }

    public static class ServiceHolder {
        public TccParticipant service = new SampleTccParticipant();
    }
}
