/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.introspect.AnnotatedConstructor;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.introspect.BasicBeanDescription;
import org.testcontainers.shaded.org.apache.commons.lang3.SerializationUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotatedConstructorTest {
    @Test
    void invokesAConstructorObtainedThroughJacksonIntrospection() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        BasicBeanDescription description = (BasicBeanDescription) mapper
            .getDeserializationConfig()
            .introspect(mapper.constructType(ConstructorBean.class));

        AnnotatedConstructor constructor = description.findDefaultConstructor();
        AnnotatedConstructor restored = SerializationUtils.roundtrip(constructor);

        assertThat(constructor.call()).isInstanceOf(ConstructorBean.class);
        assertThat(constructor.call(new Object[0])).isInstanceOf(ConstructorBean.class);
        assertThat(restored.call()).isInstanceOf(ConstructorBean.class);
    }

    public static class ConstructorBean {
        public ConstructorBean() { }
    }
}
