/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import io.sundr.deps.org.apache.commons.collections.BeanMap;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

public class BeanMapTest {
    @Test
    void exercisesPublicApi() throws Exception {
        Bean bean = new Bean();
        BeanMap map = new BeanMap(bean);
        assertEquals("initial", map.put("name", "changed"));
        assertEquals("changed", bean.getName());
        map.put("amount", "12345");
        assertEquals(new BigInteger("12345"), bean.getAmount());
        BeanMap copy = (BeanMap) map.clone();
        assertNotSame(bean, copy.getBean());
        assertEquals("changed", copy.get("name"));
        assertEquals(new BigInteger("12345"), copy.get("amount"));
        map.clear();
        assertEquals("initial", map.get("name"));
        assertEquals("changed", copy.get("name"));
    }

    public static class Bean {
        private String name = "initial";
        private BigInteger amount = BigInteger.ZERO;

        public Bean() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public BigInteger getAmount() {
            return amount;
        }

        public void setAmount(BigInteger amount) {
            this.amount = amount;
        }
    }
}
