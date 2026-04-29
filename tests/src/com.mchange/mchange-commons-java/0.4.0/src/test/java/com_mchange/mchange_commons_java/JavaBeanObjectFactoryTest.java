/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import com.mchange.v2.naming.JavaBeanObjectFactory;
import com.mchange.v2.naming.JavaBeanReferenceMaker;
import org.junit.jupiter.api.Test;

import javax.naming.Reference;
import java.util.Hashtable;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaBeanObjectFactoryTest {
    @Test
    void getObjectInstanceRecreatesJavaBeanPropertiesFromReference() throws Exception {
        ReferenceableBean bean = new ReferenceableBean();
        bean.setCount(7);
        bean.setName("alpha");

        JavaBeanReferenceMaker referenceMaker = new JavaBeanReferenceMaker();
        Reference reference = referenceMaker.createReference(bean);
        JavaBeanObjectFactory objectFactory = new JavaBeanObjectFactory();

        Object recreated = objectFactory.getObjectInstance(reference, null, null, new
            Hashtable<>());
        ReferenceableBean recreatedBean = (ReferenceableBean) recreated;

        assertThat(recreated).isInstanceOf(ReferenceableBean.class);
        assertThat(recreatedBean.getCount()).isEqualTo(7);
        assertThat(recreatedBean.getName()).isEqualTo("alpha");
    }

    public static class ReferenceableBean {
        private Integer count;
        private String name;

        public ReferenceableBean() {
        }

        public Integer getCount() {
            return count;
        }

        public void setCount(Integer count) {
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
