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
    void retrievesPublicStaticAndInstanceFields() throws Exception {
        FieldRetrievingFactoryBean staticFactory = new FieldRetrievingFactoryBean();
        staticFactory.setTargetClass(Constants.class);
        staticFactory.setTargetField("STATIC_VALUE");
        staticFactory.afterPropertiesSet();

        Constants constants = new Constants();
        FieldRetrievingFactoryBean instanceFactory = new FieldRetrievingFactoryBean();
        instanceFactory.setTargetObject(constants);
        instanceFactory.setTargetField("instanceValue");
        instanceFactory.afterPropertiesSet();

        assertThat(staticFactory.getObject()).isEqualTo("static");
        assertThat(instanceFactory.getObject()).isEqualTo("instance");
    }

    public static class Constants {
        public static final String STATIC_VALUE = "static";
        public String instanceValue = "instance";
    }
}
