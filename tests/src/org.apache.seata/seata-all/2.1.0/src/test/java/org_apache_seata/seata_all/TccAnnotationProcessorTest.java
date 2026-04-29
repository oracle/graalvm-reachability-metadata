/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;

import org.apache.dubbo.config.annotation.Reference;
import org.apache.seata.rm.tcc.api.BusinessActionContext;
import org.apache.seata.rm.tcc.api.TwoPhaseBusinessAction;
import org.apache.seata.spring.tcc.TccAnnotationProcessor;
import org.junit.jupiter.api.Test;

public class TccAnnotationProcessorTest {
    @Test
    void processesDubboReferenceAndReplacesAnnotatedTccField() throws Exception {
        TccAnnotationProcessor processor = new TccAnnotationProcessor();
        ServiceHolder holder = new ServiceHolder();

        processor.postProcessBeforeInitialization(holder, "serviceHolder");

        assertThat(holder.service).isSameAs(holder);
    }

    @Test
    void directlyAddsTccAdviceForPublicFieldApi() throws Exception {
        TccAnnotationProcessor processor = new TccAnnotationProcessor();
        ServiceHolder holder = new ServiceHolder();
        Field serviceField = ServiceHolder.class.getField("service");

        processor.addTccAdvise(
                holder,
                "directServiceHolder",
                serviceField,
                AnnotatedTccParticipant.class);

        assertThat(holder.service).isSameAs(holder);
    }

    public static class AnnotatedTccParticipant {
        @TwoPhaseBusinessAction(
                name = "processorAction",
                commitMethod = "commitProcessorAction",
                rollbackMethod = "rollbackProcessorAction")
        public boolean prepareProcessorAction(BusinessActionContext context) {
            return true;
        }

        public boolean commitProcessorAction(BusinessActionContext context) {
            return true;
        }

        public boolean rollbackProcessorAction(BusinessActionContext context) {
            return true;
        }
    }

    public static class ServiceHolder extends AnnotatedTccParticipant {
        @Reference
        public AnnotatedTccParticipant service = new AnnotatedTccParticipant();

        @Override
        public boolean prepareProcessorAction(BusinessActionContext context) {
            return true;
        }
    }
}
