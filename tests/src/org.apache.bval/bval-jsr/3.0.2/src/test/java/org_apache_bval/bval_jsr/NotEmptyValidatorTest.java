/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_bval.bval_jsr;

import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotEmpty;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NotEmptyValidatorTest {

    @Test
    void invokesPublicIsEmptyMethodOnCustomValue() {
        try (ValidatorFactory factory = ValidationTestSupport.factory()) {
            assertThat(factory.getValidator().validate(new Bean(new Basket(true)))).hasSize(1);
            assertThat(factory.getValidator().validate(new Bean(new Basket(false)))).isEmpty();
        }
    }

    public static class Bean {
        @NotEmpty
        private final Basket basket;

        public Bean(Basket basket) {
            this.basket = basket;
        }
    }

    public static class Basket {
        private final boolean empty;

        public Basket(boolean empty) {
            this.empty = empty;
        }

        public boolean isEmpty() {
            return empty;
        }
    }
}
