/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.FieldRetrievingFactoryBean;

import static org.assertj.core.api.Assertions.assertThat;

public class FieldRetrievingFactoryBeanTest {

    @Test
    void retrievesPublicStaticFieldFromConfiguredStaticFieldName() throws Exception {
        FieldRetrievingFactoryBean factoryBean = new FieldRetrievingFactoryBean();
        factoryBean.setStaticField(PublicFieldHolder.class.getName() + ".STATIC_VALUE");

        factoryBean.afterPropertiesSet();

        assertThat(factoryBean.getObjectType()).isEqualTo(String.class);
        assertThat(factoryBean.getObject()).isEqualTo("static spring value");
    }

    @Test
    void retrievesPublicInstanceFieldFromConfiguredTargetObject() throws Exception {
        PublicFieldHolder holder = new PublicFieldHolder("instance spring value");
        FieldRetrievingFactoryBean factoryBean = new FieldRetrievingFactoryBean();
        factoryBean.setTargetObject(holder);
        factoryBean.setTargetField("instanceValue");

        factoryBean.afterPropertiesSet();

        assertThat(factoryBean.getObjectType()).isEqualTo(String.class);
        assertThat(factoryBean.getObject()).isEqualTo("instance spring value");
    }

    public static class PublicFieldHolder {
        public static final String STATIC_VALUE = "static spring value";

        public final String instanceValue;

        PublicFieldHolder(String instanceValue) {
            this.instanceValue = instanceValue;
        }
    }
}
