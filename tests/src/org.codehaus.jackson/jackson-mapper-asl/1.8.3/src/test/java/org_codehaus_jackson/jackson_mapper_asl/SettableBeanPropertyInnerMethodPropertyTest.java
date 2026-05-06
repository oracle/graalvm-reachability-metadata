/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import static org.assertj.core.api.Assertions.assertThat;

import org.codehaus.jackson.map.ObjectMapper;
import org.junit.jupiter.api.Test;

public class SettableBeanPropertyInnerMethodPropertyTest {
    @Test
    public void deserializesBeanPropertyThroughSetterMethod() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        SetterBackedBean bean = mapper.readValue("""
                {
                  "name": "method-value",
                  "count": 3
                }
                """, SetterBackedBean.class);

        assertThat(bean.getName()).isEqualTo("method-value");
        assertThat(bean.getCount()).isEqualTo(3);
        assertThat(bean.getSetterCallCount()).isEqualTo(2);
    }

    public static final class SetterBackedBean {
        private String name;
        private int count;
        private int setterCallCount;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            setterCallCount++;
            this.name = name;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            setterCallCount++;
            this.count = count;
        }

        public int getSetterCallCount() {
            return setterCallCount;
        }
    }
}
