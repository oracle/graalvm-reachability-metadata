/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_beanutils.commons_beanutils;

import org.apache.commons.beanutils.ConstructorUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConstructorUtilsTest {
    @Test
    void constructsUsingExactAndCompatibleSignatures() throws Exception {
        Message direct = (Message) ConstructorUtils.invokeConstructor(Message.class, "hello");
        Message exact = (Message) ConstructorUtils.invokeExactConstructor(Message.class, "welcome");
        Quantity compatible = (Quantity) ConstructorUtils.invokeConstructor(Quantity.class, Integer.valueOf(7));
        Quantity precise = (Quantity) ConstructorUtils.invokeExactConstructor(Quantity.class,
                new Object[]{Long.valueOf(11)}, new Class<?>[]{Number.class});

        assertEquals("hello", direct.getText());
        assertEquals("welcome", exact.getText());
        assertEquals(7, compatible.getValue());
        assertEquals(11, precise.getValue());
    }

    public static class Message {
        private final String text;

        public Message(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }
    }

    public static class Quantity {
        private final int value;

        public Quantity(Number value) {
            this.value = value.intValue();
        }

        public int getValue() {
            return value;
        }
    }
}
