/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import static org.assertj.core.api.Assertions.assertThat;

import org.codehaus.jackson.map.InjectableValues;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.annotate.JacksonInject;
import org.junit.jupiter.api.Test;

public class AnnotatedFieldTest {
    @Test
    public void injectsValueIntoAnnotatedFieldDuringDeserialization() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        InjectableValues.Std injectableValues = new InjectableValues.Std()
                .addValue("requestId", "request-123");
        mapper.setInjectableValues(injectableValues);

        FieldInjectionTarget target = mapper.readValue("{}", FieldInjectionTarget.class);

        assertThat(target.requestId).isEqualTo("request-123");
    }

    public static final class FieldInjectionTarget {
        @JacksonInject("requestId")
        public String requestId;
    }
}
