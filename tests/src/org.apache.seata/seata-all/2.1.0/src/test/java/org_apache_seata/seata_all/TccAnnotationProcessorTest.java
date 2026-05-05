/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import org.apache.seata.rm.tcc.api.BusinessActionContext;
import org.apache.seata.rm.tcc.api.TwoPhaseBusinessAction;
import org.apache.seata.spring.tcc.TccAnnotationProcessor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
public class TccAnnotationProcessorTest {
    @Test
    void addTccAdviseInspectsAnnotatedFieldTypeAndReassignsProxyCandidate() throws Exception {
        TccClientBean bean = new TccClientBean();
        Field tccReferenceField = TccClientBean.class.getField("tccReference");

        new TccAnnotationProcessor().addTccAdvise(
                bean,
                "tccClientBean",
                tccReferenceField,
                TccServiceReference.class);

        assertThat(bean.tccReference).isSameAs(bean);
    }

    public abstract static class TccServiceReference {
        @TwoPhaseBusinessAction(
                name = "annotation-processor-tcc-action",
                commitMethod = "confirm",
                rollbackMethod = "cancel")
        public abstract boolean prepare(BusinessActionContext context);
    }

    public static class TccClientBean extends TccServiceReference {
        public TccServiceReference tccReference = this;

        @Override
        public boolean prepare(BusinessActionContext context) {
            return true;
        }

        public boolean confirm(BusinessActionContext context) {
            return true;
        }

        public boolean cancel(BusinessActionContext context) {
            return true;
        }
    }
}
