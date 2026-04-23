/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JacksonSetterlessPropertyTest {

    @Test
    void setterlessPropertyUpdatesCollectionThroughGetter() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        SetterlessBean bean = mapper.readValue("{\"values\":[\"a\",\"b\"]}", SetterlessBean.class);
        assertThat(bean.getValues()).containsExactly("a", "b");
    }

    static class SetterlessBean {

        private final List<String> values = new ArrayList<String>();

        public List<String> getValues() {
            return values;
        }
    }
}
