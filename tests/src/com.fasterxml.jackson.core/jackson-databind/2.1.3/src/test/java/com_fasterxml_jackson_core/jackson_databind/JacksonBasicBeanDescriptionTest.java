/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BasicBeanDescription;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JacksonBasicBeanDescriptionTest {

    @Test
    void basicBeanDescriptionInstantiatesBeansThroughDefaultConstructor() {
        ObjectMapper mapper = new ObjectMapper();
        BasicBeanDescription description = (BasicBeanDescription) mapper.getDeserializationConfig().introspect(
                mapper.constructType(DescriptionBean.class));

        DescriptionBean bean = (DescriptionBean) description.instantiateBean(true);
        assertThat(bean.value).isEqualTo("created");
    }

    static class DescriptionBean {

        final String value;

        private DescriptionBean() {
            this.value = "created";
        }
    }
}
