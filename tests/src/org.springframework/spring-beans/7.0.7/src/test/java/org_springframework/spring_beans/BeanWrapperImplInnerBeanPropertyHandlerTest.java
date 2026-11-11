/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanWrapperImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BeanWrapperImplInnerBeanPropertyHandlerTest {
    @Test
    void readsAndWritesPropertiesWithTypeConversion() {
        Account account = new Account();
        BeanWrapperImpl wrapper = new BeanWrapperImpl(account);
        wrapper.setPropertyValue("count", "42");
        assertEquals(42, wrapper.getPropertyValue("count"));
        assertEquals(42, account.getCount());
    }

    @Test
    void selectsOverloadedWriteOnlySetterForActualValue() {
        WriteOnly bean = new WriteOnly();
        BeanWrapperImpl wrapper = new BeanWrapperImpl(bean);
        wrapper.setPropertyValue("value", new Payload("message"));
        assertEquals("message", bean.value);
    }

    public static class Account {
        private int count;

        public int getCount() {
            return this.count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }

    public record Payload(String text) {
    }

    public static class WriteOnly {
        private String value;

        public void setValue(Integer value) {
            this.value = value.toString();
        }

        public void setValue(Payload value) {
            this.value = value.text();
        }
    }
}
