/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.FieldRetrievingFactoryBean;

public class FieldRetrievingFactoryBeanTest {

    @Test
    public void retrievesPublicInstanceFieldFromTargetObject() throws Exception {
        ExampleFields target = new ExampleFields("instance value");
        FieldRetrievingFactoryBean factoryBean = new FieldRetrievingFactoryBean();
        factoryBean.setTargetObject(target);
        factoryBean.setTargetField("instanceField");

        factoryBean.afterPropertiesSet();

        assertThat(factoryBean.getObjectType()).isEqualTo(String.class);
        assertThat(factoryBean.getObject()).isEqualTo("instance value");
        target.instanceField = "changed value";
        assertThat(factoryBean.getObject()).isEqualTo("changed value");
    }

    @Test
    public void retrievesPublicStaticFieldFromTargetClass() throws Exception {
        FieldRetrievingFactoryBean factoryBean = new FieldRetrievingFactoryBean();
        factoryBean.setTargetClass(ExampleFields.class);
        factoryBean.setTargetField("STATIC_FIELD");

        factoryBean.afterPropertiesSet();

        assertThat(factoryBean.getObjectType()).isEqualTo(String.class);
        assertThat(factoryBean.getObject()).isEqualTo("static value");
    }

    public static class ExampleFields {
        public static final String STATIC_FIELD = "static value";

        public String instanceField;

        public ExampleFields(String instanceField) {
            this.instanceField = instanceField;
        }
    }
}
