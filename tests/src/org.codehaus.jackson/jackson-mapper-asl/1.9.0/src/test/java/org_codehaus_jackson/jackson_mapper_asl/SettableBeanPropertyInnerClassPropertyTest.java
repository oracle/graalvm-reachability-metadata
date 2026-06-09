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

public class SettableBeanPropertyInnerClassPropertyTest {
    @Test
    public void deserializesNonStaticInnerClassProperty() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        OuterBean bean = mapper.readValue("""
                {
                  "child": {
                    "value": "nested-value",
                    "count": 7
                  }
                }
                """, OuterBean.class);

        assertThat(bean.child).isNotNull();
        assertThat(bean.child.value).isEqualTo("nested-value");
        assertThat(bean.child.count).isEqualTo(7);
        assertThat(bean.child.getOuterBean()).isSameAs(bean);
        assertThat(bean.getChildConstructorCalls()).isEqualTo(1);
    }

    public static final class OuterBean {
        public ChildBean child;
        private int childConstructorCalls;

        public int getChildConstructorCalls() {
            return childConstructorCalls;
        }

        public class ChildBean {
            public String value;
            public int count;

            public ChildBean() {
                childConstructorCalls++;
            }

            public OuterBean getOuterBean() {
                return OuterBean.this;
            }
        }
    }
}
