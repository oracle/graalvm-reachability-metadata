/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.io.BeanSerializerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class BeanDeserializerTest {
    @Test
    void restoresPropertiesAndResolvesBean() throws Exception {
        Bean value = new Bean("initial");
        value.setName("follower");
        assertEquals("resolved:follower", SerializationSupport.roundTrip(value, new BeanSerializerFactory(), false));
    }

    public static class Bean {
        private String name;

        public Bean(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Object readResolve() {
            return "resolved:" + name;
        }
    }
}
