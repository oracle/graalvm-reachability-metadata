/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_beanutils.commons_beanutils;

import org.apache.commons.beanutils.BeanMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeanMapTest {
    @Test
    void readsWritesAndConvertsBeanProperties() {
        Order order = new Order();
        BeanMap map = new BeanMap(order);

        assertNull(map.put("code", "widget"));
        assertEquals("widget", order.getCode().getValue());
        assertEquals(order.getCode(), map.get("code"));
        assertEquals("new", map.put("status", "ready"));
        assertEquals("ready", order.getStatus());
        assertTrue(map.containsKey("status"));
    }

    @Test
    void clonesAndResetsTheUnderlyingBean() throws Exception {
        Order order = new Order();
        order.setStatus("ready");
        BeanMap original = new BeanMap(order);
        BeanMap copy = (BeanMap) original.clone();

        assertNotSame(order, copy.getBean());
        assertEquals("ready", copy.get("status"));
        copy.put("status", "shipped");
        assertEquals("ready", original.get("status"));

        original.clear();
        assertNotSame(order, original.getBean());
        assertEquals("new", original.get("status"));
        assertEquals("ready", order.getStatus());
    }

    public static class Code {
        private final String value;

        public Code(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public static class Order {
        private Code code;
        private String status = "new";

        public Order() {
        }

        public Code getCode() {
            return code;
        }

        public void setCode(Code code) {
            this.code = code;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
