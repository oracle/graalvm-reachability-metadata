/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SetterlessPropertyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void deserializesCollectionThroughGetterBackedSetterlessProperty() throws JsonProcessingException {
        SetterlessCollectionBean bean = MAPPER.readValue("""
                {"values":["alpha","bravo"]}
                """, SetterlessCollectionBean.class);

        assertThat(bean.getValues()).containsExactly("alpha", "bravo");
    }

    public static final class SetterlessCollectionBean {
        private final CollectionState state = new CollectionState();

        public List<String> getValues() {
            return state.values;
        }
    }

    private static final class CollectionState {
        private final List<String> values = new ArrayList<>();
    }
}
