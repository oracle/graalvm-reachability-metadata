/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_collections.commons_collections;

import org.apache.commons.collections.BeanMap;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeanMapTest {
    @Test
    void readsAndWritesBeanProperties() {
        AccountBean account = new AccountBean();
        BeanMap properties = new BeanMap(account);

        assertSame(account, properties.getBean());
        assertTrue(properties.containsKey("name"));
        assertFalse(properties.containsKey("unknown"));
        assertEquals("guest", properties.get("name"));
        assertEquals("guest", properties.put("name", "Morgan"));
        assertEquals("Morgan", account.getName());
        assertEquals("Morgan", properties.get("name"));

        account.setName("Taylor");
        assertEquals("Taylor", properties.get("name"));
    }

    @Test
    void convertsPropertyValuesUsingConstructorsAndPrimitiveTransformers() {
        AccountBean account = new AccountBean();
        BeanMap properties = new BeanMap(account);

        assertEquals(BigDecimal.ZERO, properties.put("balance", "42.75"));
        assertEquals(new BigDecimal("42.75"), account.getBalance());
        assertEquals(new BigDecimal("42.75"), properties.get("balance"));

        assertEquals(3, properties.put("limit", "12"));
        assertEquals(12, account.getLimit());
        assertEquals(12, properties.get("limit"));
    }

    @Test
    void clearReplacesTheBeanWithDefaultPropertyValues() {
        AccountBean account = new AccountBean();
        account.setName("Morgan");
        account.setLimit(12);
        account.setBalance(new BigDecimal("42.75"));
        BeanMap properties = new BeanMap(account);
        int propertyCount = properties.size();

        properties.clear();

        assertNotSame(account, properties.getBean());
        assertEquals(propertyCount, properties.size());
        assertEquals("guest", properties.get("name"));
        assertEquals(3, properties.get("limit"));
        assertEquals(BigDecimal.ZERO, properties.get("balance"));
        assertEquals("Morgan", account.getName());
        assertEquals(12, account.getLimit());
        assertEquals(new BigDecimal("42.75"), account.getBalance());

        assertEquals("guest", properties.put("name", "Taylor"));
        assertEquals("Taylor", properties.get("name"));
        assertEquals("Morgan", account.getName());
    }

    @Test
    void cloneCopiesPropertiesIntoAnIndependentBean() throws CloneNotSupportedException {
        AccountBean account = new AccountBean();
        account.setName("Morgan");
        account.setLimit(12);
        account.setBalance(new BigDecimal("42.75"));
        BeanMap properties = new BeanMap(account);

        BeanMap copy = (BeanMap) properties.clone();

        assertNotSame(properties, copy);
        assertNotSame(account, copy.getBean());
        assertEquals("Morgan", copy.get("name"));
        assertEquals(12, copy.get("limit"));
        assertEquals(new BigDecimal("42.75"), copy.get("balance"));
        assertEquals("Morgan", copy.put("name", "Taylor"));
        assertEquals("Taylor", copy.get("name"));
        assertEquals("Morgan", properties.get("name"));
        assertEquals("Morgan", account.getName());
    }

    public static class AccountBean {
        private String name = "guest";
        private int limit = 3;
        private BigDecimal balance = BigDecimal.ZERO;

        public AccountBean() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }

        public BigDecimal getBalance() {
            return balance;
        }

        public void setBalance(BigDecimal balance) {
            this.balance = balance;
        }
    }
}
