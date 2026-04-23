/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JacksonSettableAnyPropertyTest {

    @Test
    void anySetterInvokesMethodForUnknownProperties() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AnySetterBean bean = mapper.readValue("{\"first\":\"value\"}", AnySetterBean.class);
        assertThat(bean.values).containsEntry("first", "value");
    }

    static class AnySetterBean {

        final Map<String, Object> values = new LinkedHashMap<String, Object>();

        @JsonAnySetter
        public void add(String name, Object value) {
            values.put(name, value);
        }
    }
}
