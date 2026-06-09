/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.codehaus.jackson.map.ObjectMapper;
import org.junit.jupiter.api.Test;

public class SettableBeanPropertyInnerSetterlessPropertyTest {
    @Test
    public void deserializesCollectionThroughGetterWithoutSetter() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        GetterBackedBean bean = mapper.readValue("""
                {
                  "items": ["first", "second"]
                }
                """, GetterBackedBean.class);

        assertThat(bean.currentItems()).containsExactly("first", "second");
        assertThat(bean.getGetterCallCount()).isEqualTo(1);
    }

    public static final class GetterBackedBean {
        private final List<String> values = new ArrayList<String>();
        private int getterCallCount;

        public List<String> getItems() {
            getterCallCount++;
            return values;
        }

        public List<String> currentItems() {
            return values;
        }

        public int getGetterCallCount() {
            return getterCallCount;
        }
    }
}
