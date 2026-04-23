/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JacksonInnerClassPropertyTest {

    @Test
    void innerClassPropertyCreatesNestedValueWithOuterInstance() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        OuterContainer container = mapper.readValue("{\"child\":{\"name\":\"Ada\"}}", OuterContainer.class);
        assertThat(container.child.name).isEqualTo("Ada");
    }

    static class OuterContainer {

        public Child child;

        public class Child {

            public String name;
        }
    }
}
