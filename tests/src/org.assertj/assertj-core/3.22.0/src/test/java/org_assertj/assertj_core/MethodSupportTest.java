/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_assertj.assertj_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

public class MethodSupportTest {
    @Test
    void extractsResultsOfPublicNoArgumentMethods() {
        List<Order> orders = List.of(new Order("A", 2), new Order("B", 5));
        assertThat(orders).extractingResultOf("summary", String.class).containsExactly("A:2", "B:5");
        assertThat(new Order[]{new Order("C", 3)}).extractingResultOf("summary").containsExactly("C:3");
    }

    @Test
    void rejectsMethodsThatDoNotReturnAValue() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> assertThat(List.of(new Order("A", 2))).extractingResultOf("clear"));
        assertThat(failure).hasMessageContaining("return a value");
    }

    public static class Order {
        private final String code;
        private int quantity;

        public Order(String code, int quantity) {
            this.code = code;
            this.quantity = quantity;
        }

        public String summary() {
            return code + ":" + quantity;
        }

        public void clear() {
            quantity = 0;
        }
    }
}
