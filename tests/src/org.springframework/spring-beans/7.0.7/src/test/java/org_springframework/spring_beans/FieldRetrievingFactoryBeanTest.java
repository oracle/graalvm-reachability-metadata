/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.FieldRetrievingFactoryBean;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FieldRetrievingFactoryBeanTest {
    @Test
    void retrievesInstanceAndStaticFields() throws Exception {
        FieldRetrievingFactoryBean instance = new FieldRetrievingFactoryBean();
        instance.setTargetObject(new Constants());
        instance.setTargetField("name");
        instance.afterPropertiesSet();
        assertEquals("instance", instance.getObject());
        assertEquals(String.class, instance.getObjectType());
        FieldRetrievingFactoryBean constant = new FieldRetrievingFactoryBean();
        constant.setStaticField(Constants.class.getName() + ".DEFAULT_NAME");
        constant.afterPropertiesSet();
        assertEquals("default", constant.getObject());
    }

    public static class Constants {
        public static final String DEFAULT_NAME = "default";
        public String name = "instance";
    }
}
