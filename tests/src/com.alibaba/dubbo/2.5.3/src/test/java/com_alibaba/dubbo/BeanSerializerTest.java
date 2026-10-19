/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.com.caucho.hessian.io.BeanSerializerFactory;
import org.junit.jupiter.api.Test;

public class BeanSerializerTest {
    @Test
    void serializesBeanPropertiesThroughAccessors() throws Exception {
        Bean source = new Bean();
        source.setName("Ada");
        source.setCount(3);

        Bean copy = (Bean) HessianTestSupport.roundTrip(source, new BeanSerializerFactory());

        assertThat(copy.getName()).isEqualTo("Ada");
        assertThat(copy.getCount()).isEqualTo(3);
    }

    public static class Bean {
        private String name;
        private int count;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }
}
