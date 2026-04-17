/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import java.lang.reflect.Field;

import org.apache.seata.rm.tcc.api.BusinessActionContext;
import org.apache.seata.rm.tcc.api.TwoPhaseBusinessAction;
import org.apache.seata.spring.tcc.TccAnnotationProcessor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TccAnnotationProcessorTest {
    @Test
    void addTccAdviseReassignsTheAnnotatedReferenceField() throws Exception {
        TccAnnotationProcessor processor = new TccAnnotationProcessor();
        TccClientBean bean = new TccClientBean();
        Field referenceField = TccClientBean.class.getField("reference");

        assertThat(bean.reference).isNotSameAs(bean);

        processor.addTccAdvise(bean, "tccClientBean", referenceField, AnnotatedReferenceService.class);

        assertThat(bean.reference).isSameAs(bean);
    }

    public abstract static class AnnotatedReferenceService {
        @TwoPhaseBusinessAction(name = "annotation-processor-action")
        public abstract boolean prepare(BusinessActionContext actionContext);

        public abstract boolean commit(BusinessActionContext actionContext);

        public abstract boolean rollback(BusinessActionContext actionContext);
    }

    public static final class TccClientBean extends AnnotatedReferenceService {
        public AnnotatedReferenceService reference = new StandaloneReferenceService();

        @Override
        public boolean prepare(BusinessActionContext actionContext) {
            return true;
        }

        @Override
        public boolean commit(BusinessActionContext actionContext) {
            return true;
        }

        @Override
        public boolean rollback(BusinessActionContext actionContext) {
            return true;
        }
    }

    public static final class StandaloneReferenceService extends AnnotatedReferenceService {
        @Override
        public boolean prepare(BusinessActionContext actionContext) {
            return true;
        }

        @Override
        public boolean commit(BusinessActionContext actionContext) {
            return true;
        }

        @Override
        public boolean rollback(BusinessActionContext actionContext) {
            return true;
        }
    }
}
