/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import javax.naming.Reference;

import com.mchange.v2.naming.JavaBeanObjectFactory;
import com.mchange.v2.naming.JavaBeanReferenceMaker;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaBeanObjectFactoryTest {
    @Test
    void getObjectInstanceRecreatesBeanFromReference() throws Exception {
        Reference referenceMakerOutput = new JavaBeanReferenceMaker().createReference(createBean());

        Object recreated = new JavaBeanObjectFactory().getObjectInstance(referenceMakerOutput, null, null, null);

        assertThat(recreated).isInstanceOf(SampleBean.class);
        SampleBean bean = (SampleBean) recreated;
        assertThat(bean.getCount()).isEqualTo(7);
        assertThat(bean.getName()).isEqualTo("restored");
    }

    private static SampleBean createBean() {
        SampleBean bean = new SampleBean();
        bean.setCount(7);
        bean.setName("restored");
        return bean;
    }

    public static final class SampleBean {
        private int count;
        private String name;

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
