/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.FieldRetrievingFactoryBean;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FieldRetrievingFactoryBeanTest {
    @Test
    void retrievesStaticAndInstanceFields() throws Exception {
        FieldRetrievingFactoryBean constant = new FieldRetrievingFactoryBean();
        constant.setTargetClass(TestBeans.Fields.class);
        constant.setTargetField("CATEGORY");
        constant.afterPropertiesSet();
        assertEquals("sample", constant.getObject());
        TestBeans.Fields bean = new TestBeans.Fields();
        FieldRetrievingFactoryBean instance = new FieldRetrievingFactoryBean();
        instance.setTargetObject(bean);
        instance.setTargetField("label");
        instance.afterPropertiesSet();
        assertEquals("before", instance.getObject());
        bean.label = "updated";
        assertEquals("updated", instance.getObject());
    }
}
