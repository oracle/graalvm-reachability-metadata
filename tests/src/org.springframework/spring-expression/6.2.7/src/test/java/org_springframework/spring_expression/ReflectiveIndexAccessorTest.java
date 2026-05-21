/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_expression;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.support.ReflectiveIndexAccessor;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectiveIndexAccessorTest {
    @Test
    void resolvesPublicReadMethodWhenConstructingReadOnlyAccessor() {
        ReflectiveIndexAccessor accessor = new ReflectiveIndexAccessor(FruitBasket.class, Color.class, "getFruit");
        StandardEvaluationContext context = new StandardEvaluationContext();
        FruitBasket basket = new FruitBasket();

        assertThat(accessor.getSpecificTargetClasses())
                .containsExactly(FruitBasket.class);
        assertThat(accessor.canRead(context, basket, Color.RED))
                .isTrue();
        TypedValue value = accessor.read(context, basket, Color.RED);
        assertThat(value.getValue())
                .isEqualTo("cherry");
        assertThat(accessor.canWrite(context, basket, Color.RED))
                .isFalse();
    }

    @Test
    void resolvesPublicReadAndWriteMethodsWhenConstructingReadWriteAccessor() {
        ReflectiveIndexAccessor accessor = new ReflectiveIndexAccessor(
                FruitBasket.class, Color.class, "getFruit", "setFruit");
        StandardEvaluationContext context = new StandardEvaluationContext();
        FruitBasket basket = new FruitBasket();

        assertThat(accessor.canRead(context, basket, Color.ORANGE))
                .isTrue();
        assertThat(accessor.canWrite(context, basket, Color.ORANGE))
                .isTrue();

        accessor.write(context, basket, Color.ORANGE, "tangerine");

        assertThat(basket.getFruit(Color.ORANGE))
                .isEqualTo("tangerine");
        assertThat(accessor.read(context, basket, Color.ORANGE).getValue())
                .isEqualTo("tangerine");
    }

    public enum Color {
        RED, ORANGE
    }

    public static final class FruitBasket {
        private final Map<Color, String> fruits = new EnumMap<>(Color.class);

        public FruitBasket() {
            this.fruits.put(Color.RED, "cherry");
            this.fruits.put(Color.ORANGE, "orange");
        }

        public String getFruit(Color color) {
            return this.fruits.get(color);
        }

        public void setFruit(Color color, String fruit) {
            this.fruits.put(color, fruit);
        }
    }
}
